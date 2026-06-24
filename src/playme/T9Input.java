package playme;

/**
 * Motor de busqueda T9 para teclado numerico del Nokia.
 * Convierte pulsaciones de teclas numericas en letras
 * y busca coincidencias en el catalogo.
 *
 * Mapeo estandar T9:
 * 2=abc  3=def  4=ghi  5=jkl  6=mno  7=pqrs  8=tuv  9=wxyz
 */
public class T9Input {

    private static final String[] T9_MAP = {
        "",      // 0
        "",      // 1
        "abc",   // 2
        "def",   // 3
        "ghi",   // 4
        "jkl",   // 5
        "mno",   // 6
        "pqrs",  // 7
        "tuv",   // 8
        "wxyz"   // 9
    };

    private StringBuffer input; // digitos pulsados
    private StringBuffer display; // texto que se muestra
    private int lastKey;
    private int keyPressCount;
    private long lastKeyTime;
    private static final long MULTI_TAP_DELAY = 800; // ms

    public T9Input() {
        input = new StringBuffer();
        display = new StringBuffer();
        lastKey = -1;
        keyPressCount = 0;
        lastKeyTime = 0;
    }

    /**
     * Procesa una pulsacion de tecla numerica (0-9).
     * Retorna true si el input cambio.
     */
    public boolean keyPressed(int key) {
        if (key < 2 || key > 9) {
            if (key == 0) {
                // Espacio
                commitCurrentChar();
                display.append(' ');
                input.append('0');
                return true;
            }
            return false;
        }

        long now = System.currentTimeMillis();

        if (key == lastKey && (now - lastKeyTime) < MULTI_TAP_DELAY) {
            // Misma tecla, ciclar letra
            keyPressCount++;
            String letters = T9_MAP[key];
            int idx = keyPressCount % letters.length();
            // Reemplazar ultima letra
            if (display.length() > 0) {
                display.setCharAt(display.length() - 1, letters.charAt(idx));
            }
        } else {
            // Nueva tecla - confirmar anterior
            commitCurrentChar();
            keyPressCount = 0;
            lastKey = key;
            String letters = T9_MAP[key];
            display.append(letters.charAt(0));
        }

        lastKeyTime = now;
        return true;
    }

    /** Borra el ultimo caracter */
    public boolean backspace() {
        if (display.length() > 0) {
            display.deleteCharAt(display.length() - 1);
            if (input.length() > 0) {
                input.deleteCharAt(input.length() - 1);
            }
            lastKey = -1;
            keyPressCount = 0;
            return true;
        }
        return false;
    }

    /** Limpia todo el input */
    public void clear() {
        input.setLength(0);
        display.setLength(0);
        lastKey = -1;
        keyPressCount = 0;
    }

    /** Confirma el caracter actual antes de nueva tecla */
    private void commitCurrentChar() {
        if (lastKey >= 2 && lastKey <= 9 && display.length() > 0) {
            // El caracter ya esta en display, anadir digito al input
            input.append((char)('0' + lastKey));
        }
        lastKey = -1;
        keyPressCount = 0;
    }

    /** Obtiene el texto actual para mostrar */
    public String getDisplayText() {
        return display.toString();
    }

    /** Obtiene el texto para buscar (confirmado) */
    public String getSearchText() {
        commitCurrentChar();
        return display.toString();
    }

    /** Verifica si hay texto de busqueda */
    public boolean hasInput() {
        return display.length() > 0;
    }

    /**
     * Verifica si un titulo coincide con el texto de busqueda actual.
     * Busqueda case-insensitive por contenido.
     */
    public boolean matches(String title) {
        if (display.length() == 0) return true;
        String search = display.toString().toLowerCase();
        String target = title.toLowerCase();
        return target.indexOf(search) >= 0;
    }

    /**
     * Obtiene la letra actual que se esta seleccionando (para mostrar).
     * Retorna 0 si no hay seleccion activa.
     */
    public char getCurrentChar() {
        long now = System.currentTimeMillis();
        if (lastKey >= 2 && lastKey <= 9 && (now - lastKeyTime) < MULTI_TAP_DELAY) {
            String letters = T9_MAP[lastKey];
            return letters.charAt(keyPressCount % letters.length());
        }
        return 0;
    }
}
