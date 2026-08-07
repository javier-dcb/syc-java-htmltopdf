package com.syc;

import com.syc.pdf.BatchPdfGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestPdfGenerator {

    public static void main(String[] args) {
        System.out.println("=== Iniciando prueba de generación de PDF por lotes ===");
        
        testFromFile();
       //Test1();
        // String html = "<tbody>" + 
        //                 "<tr>" +
        //                 "<td>Noviembre 2025</td>" + 
        //                 "</tr>" +
        //                 "<!--{C}%3C!%2D%2D%20Banner%20principal%20%2D%2D%3E-->" +
        //                 "<tr>" + 
        //                 "<td><img alt=\"Banner Tasa de Seguridad e Higiene\" src=\"https://placehold.co/750x136/1abc9c/ffffff?text=Banner+Tasa+de+Seguridad+e+Higiene\" /></td>\r\n" +
        //                 "</tr>";
        // String urlok = BatchPdfGenerator.fixImageUrls(html);
        // System.out.println(urlok);
        // urlok = BatchPdfGenerator.fixImageUrls(html);
        // System.out.println(urlok);
        
    }

    private static void Test1() {
        // 1. Simular una lista de HTMLs recuperados de la Base de Datos
        List<String> htmlList = new ArrayList<>();

        // HTML 1: Un aviso de deuda bien estructurado con tabla y CSS
        htmlList.add(
            "<h2>AVISO DE DEUDA #001</h2>" +
            "<p>Estimado contribuyente, registra una deuda pendiente en su cuenta:</p>" +
            "<table>" +
            "  <thead><tr><th>Concepto</th><th>Vencimiento</th><th>Monto</th></tr></thead>" +
            "  <tbody>" +
            "    <tr><td>Impuesto Automotor</td><td>10/05/2026</td><td>$15,400.00</td></tr>" +
            "    <tr><td>Tasa Municipal</td><td>15/06/2026</td><td>$8,200.00</td></tr>" +
            "  </tbody>" +
            "</table>" +
            "<p><strong>Total a pagar: $23,600.00</strong></p>"
        );

        // HTML 2: Simular HTML "MAL FORMADO" de la DB (sin cerrar tags como <br> e <img>, atributos sin comillas)
        // jsoup se encargará de corregirlo automáticamente antes de pasarlo a Open HTML to PDF
        htmlList.add(
            "<h2>AVISO DE DEUDA #002</h2>" +
            "<p>Estimado vecino:<br>Le recordamos regularizar su situación laboral y fiscal." +
            "<br>Consulte los medios de pago adheridos." +
            "<hr>" + // Tag sin cerrar
            "<table>" +
            "  <tr><td>Subtotal:</td><td>$5,000.00</td></tr>" +
            "</table>"
        );

        // HTML 3: Aviso simple con formato
        htmlList.add(
            "<h2 style='color: #d9534f;'>AVISO DE DEUDA #003 (URGENTE)</h2>" +
            "<p>Su cuenta presenta un atraso de más de 60 días.</p>" +
            "<div style='background-color: #f8d7da; padding: 10px; border: 1px solid #f5c6cb;'>" +
            "   <strong>Atención:</strong> Evite el corte del servicio realizando el pago a la brevedad." +
            "</div>"
        );

        // 2. Definir la ruta de salida para el PDF de prueba
        String outputPath = "./resultado_lote_prueba.pdf";

        // Asegurar que la carpeta C:/temp/ exista o cambiar la ruta a una válida
        // java.io.File tempDir = new java.io.File("C:/temp");
        // if (!tempDir.exists()) {
        //     tempDir.mkdirs();
        // }

        // 3. Ejecutar la generación del PDF
        long startTime = System.currentTimeMillis();
        boolean success = BatchPdfGenerator.generatePdfBatch(htmlList, outputPath, true);
        long endTime = System.currentTimeMillis();

        if (success) {
            System.out.println("✅ ¡PDF generado con éxito!");
            System.out.println("📄 Archivo guardado en: " + outputPath);
            System.out.println("⏱️ Tiempo de procesamiento: " + (endTime - startTime) + " ms");
        } else {
            System.err.println("❌ Ocurrió un error al generar el PDF.");
        }
    }

    public static void testFromFile() {
        System.out.println("=== Iniciando prueba con archivo HTML ===");

        int totalDocumentos = 20000; // Número de documentos en el lote
        String filePath = "C:\\jcardozo-syc\\GestorIA\\Peticiones\\RM-57216\\CampanaTest.html";
        String outputPdfPath = "C:\\jcardozo-syc\\GestorIA\\Peticiones\\RM-57216\\CampanaTest_resultado.pdf";

        try {
            Path path = Paths.get(filePath);

            // 1. Verificar si el archivo existe
            if (!Files.exists(path)) {
                System.err.println("❌ No se encontró el archivo en la ruta: " + filePath);
                return;
            }

            System.out.println("📄 Leyendo archivo: " + path.getFileName());

            // 2. Leer el contenido del HTML en UTF-8
            String htmlContent = Files.readString(path, StandardCharsets.UTF_8);

            // 3. Empaquetar en una lista para el generador por lotes
            //List<String> htmlList = Collections.singletonList(htmlContent);
            
            // Repetimos el HTML N veces para la prueba de carga
            List<String> htmlList = Collections.nCopies(totalDocumentos, htmlContent);

        System.out.println("🚀 Procesando lote de " + totalDocumentos + " documentos...");

            // 4. Generar el PDF (fitToPage = true para probar el escalado)
            long startTime = System.currentTimeMillis();
            boolean success = BatchPdfGenerator.generatePdfBatch(htmlList, outputPdfPath, true);
            long endTime = System.currentTimeMillis();

            if (success) {
                System.out.println("✅ ¡PDF generado con éxito!");
                System.out.println("📄 Guardado en: " + outputPdfPath);
                System.out.println("⏱️ Tiempo: " + (endTime - startTime) + " ms");
            } else {
                System.err.println("❌ Ocurrió un error al generar el PDF.");
            }

        } catch (IOException e) {
            System.err.println("❌ Error al leer el archivo HTML:");
            e.printStackTrace();
        }
    }
}