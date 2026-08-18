package com.syc.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder; 
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.List;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import java.io.*;
import java.util.ArrayList;

public class BatchPdfGenerator {

    private static final int BATCH_SIZE = 500; // Tamaño óptimo de bloque para el Heap

    public static boolean generatePdfBatch(List<String> rawHtmlList, String outputPath, boolean fitToPage, int timeoutSeconds) {
        if (rawHtmlList == null || rawHtmlList.isEmpty()) {
            return false;
        }

        List<File> tempFiles = new ArrayList<>();
        try {
            // Si son pocos documentos, procesar directamente en memoria
            if (rawHtmlList.size() <= BATCH_SIZE) {
                boolean success = processChunk(rawHtmlList, new File(outputPath), fitToPage, timeoutSeconds);
                if (!success) {
                    throw new IOException("Error al generar PDF");
                }
                return success;
            }

            // Si es un lote masivo (ej. 30.000), procesar por micro-lotes
            int total = rawHtmlList.size();

            for (int i = 0; i < total; i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, total);
                List<String> subList = rawHtmlList.subList(i, end);

                File tempPdf = File.createTempFile("pdf_chunk_" + (i / BATCH_SIZE), ".tmp");
                tempFiles.add(tempPdf);

                boolean success = processChunk(subList, tempPdf, fitToPage, timeoutSeconds);
                if (!success) {
                    throw new IOException("Error procesando el sub-lote de " + i + " a " + end);
                }

                // Sugerir la recolección de basura entre bloques
                System.gc(); 
            }

            // Concatenar todos los fragmentos temporales en el archivo final
            mergePdfFilesList(tempFiles, outputPath);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            // Limpieza estricta de archivos temporales
            for (File temp : tempFiles) {
                if (temp.exists()) {
                    temp.delete();
                }
            }
        }
    }

     public static boolean htmlToPdf(String rawHtml, String outputPath, boolean fitToPage, int timeoutSeconds) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return false;
        }

        List<String> rawHtmlList = List.of(rawHtml);
        return processChunk(rawHtmlList, new File(outputPath), fitToPage, timeoutSeconds);
    }

   /**
     * Parsea el HTML y aplica la configuración para asegurar salida compatible con XHTML.
     */
    private static Document parseAndConfigureXhtml(String rawHtml) {
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            return Jsoup.parse("");
        }

        // Si en el futuro reactivas fixImageUrls, lo aplicas aquí:
        // String htmlToParse = fixImageUrls(rawHtml);

        // Limpiar los comentarios del HTML usando Regex (cubre múltiples líneas)
        String htmlToParse = rawHtml.replaceAll("(?s)<!--.*?-->", "");
        
        Document doc = Jsoup.parse(htmlToParse);
        doc.outputSettings()
           .syntax(Document.OutputSettings.Syntax.xml)
           .escapeMode(Entities.EscapeMode.xhtml)
           .charset("UTF-8");

        return doc;
    }

    /**
     * Extrae y concatena el texto de todos los bloques <style> presentes en un documento HTML.
     */
    private static String extractStyles(Document doc) {
        if (doc == null) {
            return "";
        }
        
        StringBuilder stylesBuilder = new StringBuilder();
        Elements styleElements = doc.select("style");
        
        for (Element style : styleElements) {
            stylesBuilder.append(style.html()).append("\n");
        }
        
        return stylesBuilder.toString();
    }

    /**
     * Extrae el contenido interno del <body> de un documento HTML.
     */
    private static String extractBodyContent(Document doc) {
        if (doc == null || doc.body() == null) {
            return "";
        }
        return doc.body().html();
    }

    public static String getStylesAndBody(String rawHtml) {
       
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            return "";
        }

        Document doc = parseAndConfigureXhtml(rawHtml);
        String html_body = extractBodyContent(doc);
        String html_styles = extractStyles(doc);

        return "<head><style>" + html_styles + "</style></head><body>" + html_body + "</body>";        
    }

    public static String fixImageUrls(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return htmlContent;
        }

        Document doc = Jsoup.parse(htmlContent);
        Elements imgTags = doc.select("img[src]");

        for (Element img : imgTags) {
            String src = img.attr("src").trim();
            
            // Verificamos si no está vacía y no es un Data URI (Base64)
            if (!src.isEmpty() && !src.startsWith("data:")) {
                // Extraemos la ruta limpia omitiendo los parámetros query (?text=...)
                String basePath = src.contains("?") ? src.substring(0, src.indexOf("?")) : src;

                // Verificamos si ya tiene una extensión común de imagen al final del path.
                // Busca cualquier texto (.*) que termine ($) con un punto (\\.) seguido de 
                // //una de estas extensiones: png, jpg, jpeg, gif, bmp, svg o webp (png|...), 
                // sin importar si están en mayúsculas o minúsculas (?i).
                boolean hasExtension = basePath.matches("(?i).*\\.(png|jpg|jpeg|gif|bmp|svg|webp)$");

                if (!hasExtension) {
                    if (src.contains("?")) {
                        int queryIndex = src.indexOf("?");
                        // Inserta .png justo antes de los parámetros query: "url.com/750x136.png?text=..."
                        src = src.substring(0, queryIndex) + ".png" + src.substring(queryIndex);
                    } else {
                        // Si no tiene query parameters, simplemente agrega .png al final
                        src = src + ".png";
                    }
                    
                    // Actualiza el atributo src en el tag <img>
                    img.attr("src", src);
                }
            }
        }

        // Retorna solo el contenido dentro de <body> para mantenerlo como snippet HTML
        return doc.body().html();
    }

    private static boolean processChunk(List<String> htmlList, File outputFile, boolean fitToPage, int timeoutSeconds) {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            StringBuilder batchHtml = new StringBuilder(htmlList.size() * 1024);
            
            // 1. Extraer todos los estilos CSS del primer elemento
            Document doc = parseAndConfigureXhtml(htmlList.get(0));
            String baseStyles = extractStyles(doc);

            batchHtml.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\" /><style>")
                    // Configuración de página y márgenes globales
                    .append("  @page { size: A4 portrait; margin: 8mm; }")
                    .append("  .page-container { page-break-after: always; }")
                    .append("  .page-container:last-child { page-break-after: auto; }")
                    .append("\n").append(baseStyles);
            if (fitToPage) {
                        // --- REGLAS PARA ENCAJAR EN UNA SOLA PÁGINA ---
                        // 1. Reducir tipografía base e interlineado
                batchHtml.append("  body { font-size: 11px; line-height: 1.2; font-family: sans-serif; }")
                
                        // 2. Compactar márgenes de encabezados y párrafos
                        .append("  h1, h2, h3, h4, p { margin: 4px 0; }")
                        
                        // 3. Compactar tablas y celdas (padding reducido)
                        .append("  table { width: 100%; border-collapse: collapse; }")
                        .append("  th, td { padding: 3px 4px; }")

                        // 4. Le indica a la imagen que nunca supere el ancho del contenedor padre (la hoja o la celda de la tabla)
                        //.append("  img { height: auto; display: block; page-break-inside: avoid; max-width: 100%; }") //height: auto; display: block; page-break-inside: avoid; }")
                       
                        // --- REGULAR IMÁGENES AL ANCHO DE LA HOJA A4 ---
                        // Usamos 194mm (210mm del A4 - 16mm de márgenes)
                        .append("  img { max-width: 187mm; height: auto; display: block; margin: 0 auto; }")

                        // 5. (Opcional) Escalado global si los HTML son muy grandes
                        // .append("  .page-container { zoom: 0.90; }")
                        ; 
            }
                 
            batchHtml.append("</style></head><body>");

             // 3. Procesar y agregar el contenido del body de cada elemento
            for (String rawHtml : htmlList) {
                doc = parseAndConfigureXhtml(rawHtml);
                String cleanXhtmlBody = extractBodyContent(doc);
                
                batchHtml.append("<div class=\"page-container\">")
                         .append(cleanXhtmlBody)
                         .append("</div>");
            }

            batchHtml.append("</body></html>");

            PdfRendererBuilder builder = new PdfRendererBuilder();
             // Instanciamos el factory con 5 segundos de límite
            if (timeoutSeconds > 0) {
                TimeoutStreamFactory timeoutFactory = new TimeoutStreamFactory(timeoutSeconds);
                // Método correcto en OpenHTMLToPDF para interceptar las descargas HTTP/HTTPS
                builder.useProtocolsStreamImplementation(timeoutFactory, "http", "https");
            }
            builder.withHtmlContent(batchHtml.toString(), null);
            builder.toStream(os);
            builder.run();

            batchHtml.setLength(0); // Liberar buffer
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void mergePdfFilesList(List<File> files, String outputPath) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(outputPath);

        for (File f : files) {
            merger.addSource(f);
        }

        // Fusionar los PDFs consumiendo poca memoria
        merger.mergeDocuments(null);
    }

    public static void mergePdfFiles(List<String> pdfPathList, String outputPath) throws IOException {
        if (pdfPathList == null || pdfPathList.isEmpty()) {
            throw new IllegalArgumentException("La lista de archivos PDF a fusionar no puede estar vacía.");
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(outputPath);

        for (String path : pdfPathList) {
            File pdfFile = new File(path);
            if (!pdfFile.exists()) {
                throw new FileNotFoundException("El archivo temporal no existe: " + path);
            }
            merger.addSource(pdfFile);
        }

        // PDFBox 2.x / 3.x: Utiliza un archivo temporal de disco para el proceso de fusión
        // Evita cargar todas las estructuras de las 30.000 páginas en el Heap de Java
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
    }

}
