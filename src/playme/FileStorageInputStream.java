package playme;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

/**
 * InputStream que escribe chunks a un archivo temporal mediante FileConnection (JSR-75)
 * y luego permite leerlos secuencialmente para reproducir audio sin cargarlo en RAM.
 */
public class FileStorageInputStream extends InputStream {

    private FileConnection fc;
    private OutputStream os;
    private InputStream is;
    private String filePath;
    private boolean writing = true;

    public FileStorageInputStream(String filePath) throws IOException {
        this.filePath = filePath;
        this.fc = (FileConnection) Connector.open(filePath, Connector.READ_WRITE);
        if (fc.exists()) {
            fc.delete();
        }
        fc.create();
        this.os = fc.openOutputStream();
        this.writing = true;
    }

    /**
     * Escribe un chunk descargado en el archivo temporal.
     */
    public void writeChunk(byte[] data, int offset, int length) throws IOException {
        if (!writing || os == null) {
            throw new IOException("FILE_NOT_WRITING");
        }
        os.write(data, offset, length);
    }

    /**
     * Finaliza la escritura y abre el InputStream para lectura.
     */
    public void finishWriting() throws IOException {
        if (os != null) {
            os.flush();
            os.close();
            os = null;
        }
        this.is = fc.openInputStream();
        this.writing = false;
    }

    public int read() throws IOException {
        if (is == null) {
            return -1;
        }
        return is.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (is == null) {
            return -1;
        }
        return is.read(b, off, len);
    }

    public int available() throws IOException {
        if (is == null) {
            return 0;
        }
        return is.available();
    }

    public void close() throws IOException {
        if (is != null) {
            try { is.close(); } catch (Exception e) {}
            is = null;
        }
        if (os != null) {
            try { os.close(); } catch (Exception e) {}
            os = null;
        }
        if (fc != null) {
            try {
                if (fc.exists()) {
                    fc.delete();
                }
            } catch (Exception e) {
                // ignorar
            }
            try { fc.close(); } catch (Exception e) {}
            fc = null;
        }
    }

    public String getFilePath() {
        return filePath;
    }
}
