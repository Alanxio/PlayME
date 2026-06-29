package playme;

import java.io.InputStream;
import java.io.IOException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * InputStream que lee secuencialmente los registros de varios RecordStores.
 * Cada chunk de audio se almacena en un RecordStore independiente con nombre
 * "audio_chunk_NNN" para evitar los limites de tamano de un unico RecordStore.
 * Se basa en el patron usado por MahoMaps/mm-v1 para cache de tiles.
 */
public class RecordStoreInputStream extends InputStream {

    private static final String CHUNK_PREFIX = "audio_chunk_";

    private String[] chunkNames;
    private int currentIndex;
    private RecordStore currentRs;
    private byte[] currentRecord;
    private int currentOffset;
    private int totalBytes;
    private int bytesRead;

    public RecordStoreInputStream(int totalBytes) throws RecordStoreException {
        this.totalBytes = totalBytes;
        this.bytesRead = 0;
        this.currentIndex = 0;
        this.chunkNames = listChunkStores();
        loadNextRecord();
    }

    /**
     * Lista todos los RecordStores cuyo nombre empieza por CHUNK_PREFIX,
     * ordenandolos alfabeticamente para garantizar lectura secuencial.
     */
    private String[] listChunkStores() throws RecordStoreException {
        String[] all = RecordStore.listRecordStores();
        if (all == null) {
            return new String[0];
        }

        // Contar los que coinciden con el prefijo
        int count = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] != null && all[i].startsWith(CHUNK_PREFIX)) {
                count++;
            }
        }

        String[] result = new String[count];
        int pos = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] != null && all[i].startsWith(CHUNK_PREFIX)) {
                result[pos++] = all[i];
            }
        }

        // Ordenar alfabeticamente (los nombres con ceros a la izquierda ordenan bien)
        sortStrings(result);
        return result;
    }

    private void sortStrings(String[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[i].compareTo(arr[j]) > 0) {
                    String tmp = arr[i];
                    arr[i] = arr[j];
                    arr[j] = tmp;
                }
            }
        }
    }

    private void loadNextRecord() throws RecordStoreException {
        if (currentRs != null) {
            try { currentRs.closeRecordStore(); } catch (Exception e) {}
            currentRs = null;
        }

        if (currentIndex >= chunkNames.length) {
            currentRecord = null;
            return;
        }

        currentRs = RecordStore.openRecordStore(chunkNames[currentIndex], false);
        currentIndex++;

        if (currentRs.getNumRecords() > 0) {
            currentRecord = currentRs.getRecord(1);
        } else {
            currentRecord = new byte[0];
        }
        currentOffset = 0;
    }

    public int read() throws IOException {
        if (bytesRead >= totalBytes) {
            return -1;
        }
        if (currentRecord == null || currentOffset >= currentRecord.length) {
            try {
                loadNextRecord();
            } catch (RecordStoreException e) {
                throw new IOException("RMS_READ: " + e.getMessage());
            }
            if (currentRecord == null || currentRecord.length == 0) {
                return -1;
            }
        }
        bytesRead++;
        return currentRecord[currentOffset++] & 0xFF;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (bytesRead >= totalBytes) {
            return -1;
        }
        if (currentRecord == null || currentOffset >= currentRecord.length) {
            try {
                loadNextRecord();
            } catch (RecordStoreException e) {
                throw new IOException("RMS_READ: " + e.getMessage());
            }
            if (currentRecord == null || currentRecord.length == 0) {
                return -1;
            }
        }

        int availableInRecord = currentRecord.length - currentOffset;
        int remainingTotal = totalBytes - bytesRead;
        int canRead = len;
        if (canRead > availableInRecord) canRead = availableInRecord;
        if (canRead > remainingTotal) canRead = remainingTotal;

        System.arraycopy(currentRecord, currentOffset, b, off, canRead);
        currentOffset += canRead;
        bytesRead += canRead;
        return canRead;
    }

    public int available() {
        return totalBytes - bytesRead;
    }

    public void close() throws IOException {
        if (currentRs != null) {
            try { currentRs.closeRecordStore(); } catch (Exception e) {}
            currentRs = null;
        }
    }

    /**
     * Borra todos los RecordStores que empiecen por CHUNK_PREFIX.
     */
    public static void clearAllChunks() {
        try {
            String[] all = RecordStore.listRecordStores();
            if (all == null) return;
            for (int i = 0; i < all.length; i++) {
                if (all[i] != null && all[i].startsWith(CHUNK_PREFIX)) {
                    try {
                        RecordStore.deleteRecordStore(all[i]);
                    } catch (Exception e) {
                        // ignorar
                    }
                }
            }
        } catch (Exception e) {
            // ignorar
        }
    }

    /**
     * Genera el nombre de un RecordStore para un chunk dado.
     */
    public static String chunkName(int index) {
        String num = String.valueOf(index);
        while (num.length() < 3) {
            num = "0" + num;
        }
        return CHUNK_PREFIX + num;
    }
}
