package com.swstock.util;

import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilitário para corrigir e aprimorar a digitação de caracteres acentuados (Dead Keys)
 * em campos de texto no JavaFX sob Linux (X11 / Wayland), garantindo que teclas como
 * '´' + 'i' resultem imediatamente no caractere acentuado 'í'.
 */
public class DeadKeyHelper {

    private static final Map<String, Character> COMBINATIONS = new HashMap<>();

    static {
        // Acento agudo (´)
        COMBINATIONS.put("´+a", 'á'); COMBINATIONS.put("´+A", 'Á');
        COMBINATIONS.put("´+e", 'é'); COMBINATIONS.put("´+E", 'É');
        COMBINATIONS.put("´+i", 'í'); COMBINATIONS.put("´+I", 'Í');
        COMBINATIONS.put("´+o", 'ó'); COMBINATIONS.put("´+O", 'Ó');
        COMBINATIONS.put("´+u", 'ú'); COMBINATIONS.put("´+U", 'Ú');
        COMBINATIONS.put("´+c", 'ç'); COMBINATIONS.put("´+C", 'Ç');

        // Til (~)
        COMBINATIONS.put("~+a", 'ã'); COMBINATIONS.put("~+A", 'Ã');
        COMBINATIONS.put("~+o", 'õ'); COMBINATIONS.put("~+O", 'Õ');

        // Circunflexo (^)
        COMBINATIONS.put("^+a", 'â'); COMBINATIONS.put("^+A", 'Â');
        COMBINATIONS.put("^+e", 'ê'); COMBINATIONS.put("^+E", 'Ê');
        COMBINATIONS.put("^+i", 'î'); COMBINATIONS.put("^+I", 'Î');
        COMBINATIONS.put("^+o", 'ô'); COMBINATIONS.put("^+O", 'Ô');
        COMBINATIONS.put("^+u", 'û'); COMBINATIONS.put("^+U", 'Û');

        // Crase (`)
        COMBINATIONS.put("`+a", 'à'); COMBINATIONS.put("`+A", 'À');

        // Trema (¨)
        COMBINATIONS.put("¨+u", 'ü'); COMBINATIONS.put("¨+U", 'Ü');
    }

    public static Character combine(char deadKey, char letter) {
        String comboKey = deadKey + "+" + letter;
        return COMBINATIONS.get(comboKey);
    }

    public static void attach(TextInputControl textInput) {
        if (textInput == null) return;

        final Character[] pendingDeadKey = new Character[]{null};

        textInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            String character = event.getText();

            // Detecta tecla de acento isolada
            Character deadKeyDetected = detectDeadKey(code, character);
            if (deadKeyDetected != null) {
                pendingDeadKey[0] = deadKeyDetected;
                event.consume();
                return;
            }

            if (pendingDeadKey[0] != null) {
                char deadKey = pendingDeadKey[0];
                pendingDeadKey[0] = null; // consome o estado pendente

                if (code == KeyCode.SPACE) {
                    textInput.replaceSelection(String.valueOf(deadKey));
                    event.consume();
                    return;
                }

                if (code == KeyCode.BACK_SPACE || code == KeyCode.ESCAPE) {
                    event.consume();
                    return;
                }

                // Tenta extrair a letra digitada
                Character letter = extractLetter(code, event.getText(), event.isShiftDown());
                if (letter != null) {
                    Character combined = combine(deadKey, letter);
                    if (combined != null) {
                        textInput.replaceSelection(String.valueOf(combined));
                    } else {
                        textInput.replaceSelection(deadKey + String.valueOf(letter));
                    }
                    event.consume();
                } else {
                    // Caractere não combinável: insere o acento e deixa o evento prosseguir
                    textInput.replaceSelection(String.valueOf(deadKey));
                }
            }
        });
    }

    private static Character detectDeadKey(KeyCode code, String text) {
        if (code == KeyCode.DEAD_ACUTE) return '´';
        if (code == KeyCode.DEAD_TILDE) return '~';
        if (code == KeyCode.DEAD_CIRCUMFLEX) return '^';
        if (code == KeyCode.DEAD_GRAVE) return '`';
        if (code == KeyCode.DEAD_DIAERESIS) return '¨';

        if ("´".equals(text) || "'".equals(text) && code == KeyCode.QUOTE) {
            // Em alguns teclados o acento agudo chega como ' ou ´
            return '´';
        }
        if ("~".equals(text)) return '~';
        if ("^".equals(text)) return '^';
        if ("`".equals(text)) return '`';

        return null;
    }

    private static Character extractLetter(KeyCode code, String text, boolean shiftDown) {
        if (text != null && text.length() == 1) {
            char ch = text.charAt(0);
            if (Character.isLetter(ch)) {
                return ch;
            }
        }

        if (code != null && code.isLetterKey()) {
            char ch = code.name().charAt(0);
            return shiftDown ? Character.toUpperCase(ch) : Character.toLowerCase(ch);
        }

        return null;
    }
}
