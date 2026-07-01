package playme;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * Gestiona ajustes persistentes usando RMS (Record Management System).
 * Almacena todo en un unico registro como cadena delimitada por pipes.
 */
public class Settings {

    // Repeticion
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    // Ecualizador
    public static final int EQ_NORMAL = 0;
    public static final int EQ_BASS = 1;
    public static final int EQ_ROCK = 2;
    public static final int EQ_POP = 3;
    public static final int EQ_CLASSIC = 4;

    // Tema
    public static final int THEME_AERO = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_NOKIA = 2;

    // Perfiles de calidad de audio
    public static final int PROFILE_AUTO = 0;        // Elige el mejor bitrate que quepa
    public static final int PROFILE_QUALITY = 1;     // Preferencia 96k-128k
    public static final int PROFILE_BALANCED = 2;    // Preferencia 48k-64k
    public static final int PROFILE_PERFORMANCE = 3; // Siempre 32k

    // Bitrates disponibles (deben coincidir con el servidor)
    public static final int[] BITRATES = {32, 48, 64, 96, 128};
    // Límite RMS del dispositivo en bytes (1 MB)
    public static final int MAX_RMS_BYTES = 1 * 1024 * 1024;

    // Precarga
    public static final int PRELOAD_NONE = 0;
    public static final int PRELOAD_ONE = 1;
    public static final int PRELOAD_TWO = 2;

    private static final String STORE_NAME = "PlayMESettings";

    // Valores actuales
    public int repeat = REPEAT_OFF;
    public boolean shuffle = false;
    public int equalizer = EQ_NORMAL;
    public int theme = THEME_AERO;
    public int qualityProfile = PROFILE_AUTO;
    public int preload = PRELOAD_NONE;
    public boolean showCovers = true;
    public String serverUrl = "http://heteromorphic-squarrosely-vernice.ngrok-free.dev";
    public int volume = 80;

    // Labels para la UI
    public static final String[] REPEAT_LABELS = {"Desactivada", "Una cancion", "Toda lista"};
    public static final String[] EQ_LABELS = {"Normal", "Bass Boost", "Rock", "Pop", "Clasica"};
    public static final String[] THEME_LABELS = {"Aero Vista", "Oscuro", "Nokia Azul"};
    public static final String[] PROFILE_LABELS = {"Auto", "Calidad", "Intermedio", "Rendimiento"};
    public static final String[] PRELOAD_LABELS = {"Ninguna", "Siguiente", "Dos canciones"};
    public static final String[] BOOL_LABELS = {"Si", "No"};

    public Settings() {
        load();
    }

    /** Cargar desde RMS */
    public void load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, false);
            byte[] data = rs.getRecord(1);
            if (data != null) {
                String s = new String(data, "UTF-8");
                parse(s);
            }
        } catch (Exception e) {
            // Primer uso o datos corruptos - usar defaults
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    /** Guardar en RMS */
    public void save() {
        RecordStore rs = null;
        try {
            // Borrar y recrear para simplificar
            try {
                RecordStore.deleteRecordStore(STORE_NAME);
            } catch (RecordStoreException e) {}

            rs = RecordStore.openRecordStore(STORE_NAME, true);
            String s = serialize();
            byte[] data = s.getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
        } catch (Exception e) {
            // Error guardando - no critical
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private String serialize() {
        StringBuffer sb = new StringBuffer();
        sb.append(repeat).append('|');
        sb.append(shuffle ? 1 : 0).append('|');
        sb.append(equalizer).append('|');
        sb.append(theme).append('|');
        sb.append(qualityProfile).append('|');
        sb.append(preload).append('|');
        sb.append(showCovers ? 1 : 0).append('|');
        sb.append(serverUrl).append('|');
        sb.append(volume);
        return sb.toString();
    }

    private void parse(String s) {
        try {
            int[] pipes = new int[9];
            int count = 0;
            int pos = 0;
            // Encontrar posiciones de los pipes
            while (count < 8 && pos < s.length()) {
                int p = s.indexOf('|', pos);
                if (p < 0) break;
                pipes[count] = p;
                count++;
                pos = p + 1;
            }
            if (count < 8) return; // Datos incompletos

            repeat = parseInt(s.substring(0, pipes[0]));
            shuffle = parseInt(s.substring(pipes[0] + 1, pipes[1])) == 1;
            equalizer = parseInt(s.substring(pipes[1] + 1, pipes[2]));
            theme = parseInt(s.substring(pipes[2] + 1, pipes[3]));
            qualityProfile = parseInt(s.substring(pipes[3] + 1, pipes[4]));
            preload = parseInt(s.substring(pipes[4] + 1, pipes[5]));
            showCovers = parseInt(s.substring(pipes[5] + 1, pipes[6])) == 1;
            serverUrl = s.substring(pipes[6] + 1, pipes[7]);
            volume = parseInt(s.substring(pipes[7] + 1));

            // Validar rango del perfil (compatible con datos antiguos que
            // almacenaban bitrate directo como 32, 48, 64...)
            if (qualityProfile < PROFILE_AUTO || qualityProfile > PROFILE_PERFORMANCE) {
                qualityProfile = PROFILE_AUTO;
            }
        } catch (Exception e) {
            // Datos corruptos - mantener defaults
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Calcula el bitrate objetivo para una cancion segun el perfil activo
     * y la duracion de la cancion, respetando el limite de 1 MB en RMS.
     *
     * Formula: maxBitrate = (MAX_RMS_BYTES * 8) / (duration * 1000)
     * Redondea hacia abajo al bitrate disponible mas cercano: {32, 48, 64, 96, 128}.
     *
     * @param durationSeconds duracion de la cancion en segundos
     * @return bitrate en kbps (32, 48, 64, 96 o 128)
     */
    public int calculateBitrate(int durationSeconds) {
        if (durationSeconds <= 0) {
            // Sin duracion conocida: usar 32k por seguridad
            return 32;
        }

        // Calcular maximo bitrate que cabe en 1 MB
        // maxKbps = (MAX_RMS_BYTES * 8) / (duration * 1000)
        int maxKbps = (int) ((long) MAX_RMS_BYTES * 8 / ((long) durationSeconds * 1000));

        // Redondear hacia abajo al bitrate disponible mas cercano
        int fittingBitrate = BITRATES[0]; // 32k minimo
        for (int i = 0; i < BITRATES.length; i++) {
            if (BITRATES[i] <= maxKbps) {
                fittingBitrate = BITRATES[i];
            }
        }

        // Aplicar restricciones del perfil
        switch (qualityProfile) {
            case PROFILE_QUALITY:
                // Prefiere 96-128k, pero limitado por lo que cabe
                if (fittingBitrate >= 128) return 128;
                if (fittingBitrate >= 96) return 96;
                // Si no cabe ni 96k, devuelve lo maximo posible
                return fittingBitrate;

            case PROFILE_BALANCED:
                // Prefiere 48-64k, pero limitado por lo que cabe
                if (fittingBitrate >= 64) return 64;
                if (fittingBitrate >= 48) return 48;
                return fittingBitrate;

            case PROFILE_PERFORMANCE:
                // Siempre 32k
                return 32;

            case PROFILE_AUTO:
            default:
                // El mejor bitrate que quepa
                return fittingBitrate;
        }
    }

    /**
     * Baja un nivel de perfil de calidad para reintento ante OOM.
     * @return true si se pudo bajar, false si ya esta en el nivel mas bajo
     */
    public boolean downgradeProfile() {
        switch (qualityProfile) {
            case PROFILE_AUTO:
            case PROFILE_QUALITY:
                qualityProfile = PROFILE_BALANCED;
                return true;
            case PROFILE_BALANCED:
                qualityProfile = PROFILE_PERFORMANCE;
                return true;
            default:
                // Ya esta en PERFORMANCE, no se puede bajar mas
                return false;
        }
    }

    /**
     * Obtiene etiqueta descriptiva del perfil actual con el bitrate calculado
     * para una cancion especifica.
     */
    public String getProfileDescription(int durationSeconds) {
        int bitrate = calculateBitrate(durationSeconds);
        return PROFILE_LABELS[qualityProfile] + " (" + bitrate + "k)";
    }
}
