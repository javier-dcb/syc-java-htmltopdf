package com.syc.pdf;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class TimeoutStreamFactory implements FSStreamFactory {
    private final int timeoutMillis;

    public TimeoutStreamFactory(int timeoutSeconds) {
        this.timeoutMillis = timeoutSeconds * 1000;
    }

    // 1. Método correcto requerido por FSStreamFactory
    @Override
    public FSStream getUrl(String uri) {
        InputStream is = null;
        try {
            URL url = new URL(uri);
            URLConnection conn = url.openConnection();
            
            // Tiempos de espera configurados estrictamente
            conn.setConnectTimeout(timeoutMillis);
            conn.setReadTimeout(timeoutMillis);
            conn.setRequestProperty("User-Agent", "OpenHtmlToPdf-CustomAgent/1.0");
            
            conn.connect();
            is = conn.getInputStream();
        } catch (IOException e) {
            System.err.println("Timeout o error de red descargando: " + uri + " -> " + e.getMessage());
            // Se puede retornar null o un stream vacío controlado para que la librería no rompa la ejecución general
        }
        
        return new CustomHttpStream(is);
    }

    // 2. Implementación interna obligatoria para envolver el flujo de datos
    private static class CustomHttpStream implements FSStream {
        private final InputStream stream;

        public CustomHttpStream(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public InputStream getStream() {
            return this.stream;
        }

        @Override
        public Reader getReader() {
            if (this.stream != null) {
                return new InputStreamReader(this.stream, StandardCharsets.UTF_8);
            }
            return null;
        }
    }
}
