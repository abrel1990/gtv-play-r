package com.gtv.play.r;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buscador de canales por nombre para el reproductor nativo (el WebView está pausado
 * mientras se mira un canal, así que acá no se puede usar el buscador del index).
 *
 * Es la misma lógica que el buscador del index: ignora mayúsculas y tildes, "&" = "y",
 * números hablados ("trece" = 13), tolera errores de pronunciación (Levenshtein) y
 * entiende frases tipo "abrí Gente B", "quiero ver Telefe", "poné el canal 13".
 *
 * Java puro (sin Android) a propósito, para poder probarlo en cualquier PC.
 */
final class BuscadorCanales {

    private BuscadorCanales() {}

    /** Un canal candidato. idx = posición en la lista de MainActivity. */
    static final class Item {
        final int idx;
        final String nombre;
        final String nt;      // nombre normalizado
        String nk;            // nombre normalizado sin palabras vacías
        int sc;               // puntaje
        Item(int idx, String nombre) {
            this.idx = idx;
            this.nombre = nombre == null ? "" : nombre;
            this.nt = norm(this.nombre);
        }
    }

    static final class Resultado {
        final List<Item> items;   // ordenados, mejor primero
        final boolean claro;      // true = hay un ganador claro, se puede abrir directo
        final String consulta;    // lo que se buscó (ya sin "abrí", "canal", etc.)
        Resultado(List<Item> items, boolean claro, String consulta) {
            this.items = items; this.claro = claro; this.consulta = consulta;
        }
        Item mejor() { return items.isEmpty() ? null : items.get(0); }
    }

    private static final Set<String> STOP = new HashSet<>();
    private static final Map<String, String> NUMS = new HashMap<>();
    static {
        for (String s : "el la los las de del y e un una en al a por con que".split(" ")) STOP.add(s);
        String[] n = {"cero","0","uno","1","una","1","dos","2","tres","3","cuatro","4","cinco","5","seis","6",
            "siete","7","ocho","8","nueve","9","diez","10","once","11","doce","12","trece","13","catorce","14",
            "quince","15","dieciseis","16","diecisiete","17","dieciocho","18","diecinueve","19","veinte","20",
            "treinta","30","cuarenta","40","cincuenta","50","veintiuno","21","veintidos","22","veintitres","23",
            "veinticuatro","24","veinticinco","25","veintiseis","26","veintisiete","27","veintiocho","28","veintinueve","29"};
        for (int i = 0; i < n.length; i += 2) NUMS.put(n[i], n[i + 1]);
    }

    /** Nombres de letras como los entiende el reconocedor: "ce cinco ene" = C5N. */
    private static final Map<String, String> LETRAS = new HashMap<>();
    static {
        String[] l = {"be","b","ce","c","de","d","efe","f","ge","g","ache","h","jota","j","ka","k","ele","l","eme","m",
            "ene","n","pe","p","cu","q","erre","r","ese","s","te","t","ve","v","uve","v","equis","x","ye","y","zeta","z"};
        for (int i = 0; i < l.length; i += 2) LETRAS.put(l[i], l[i + 1]);
    }

    private static final Pattern RE_DIR = Pattern.compile(
        "^(?:(?:por favor|porfa|ok|oye|che)\\s+)?(?:abri|abrir|abre|abrime|abrirme|reproduci|reproducir|reproduce|"
        + "reproducime|pone|poner|ponme|ponele|poneme|pon|quiero ver|quiero mirar|quiero|queria ver|me gustaria ver|"
        + "ver|mirar|mira|miro|veo|sintoniza|sintonizar|cambia a|cambiar a|anda a|ir a|pasa a|pasame|llevame a)\\s+");
    private static final Pattern RE_BUS = Pattern.compile(
        "^(?:(?:por favor|porfa)\\s+)?(?:busca|buscar|buscame|busco|encontra|encontrar|encontrame)\\s+");
    private static final Pattern RE_ART = Pattern.compile(
        "^(?:(?:el|la|los|las|un|una)\\s+)?(?:canal|canales|pelicula|peliculas|serie|series|film|programa|capitulo)\\s+(?:de\\s+|del\\s+)?");
    private static final Pattern RE_COLA = Pattern.compile("\\s+(?:por favor|porfa|gracias)$");
    private static final Pattern RE_ARTICULO = Pattern.compile("^(?:el|la|los|las)\\s+");
    private static final Pattern RE_MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern RE_NOALNUM = Pattern.compile("[^a-z0-9]+");

    // ------------------------------------------------------------------ texto
    static String norm(String t) {
        if (t == null) return "";
        t = t.toLowerCase(Locale.ROOT);
        t = Normalizer.normalize(t, Normalizer.Form.NFD);
        t = RE_MARCAS.matcher(t).replaceAll("");
        t = t.replace("&", " y ");
        t = RE_NOALNUM.matcher(t).replaceAll(" ");
        return t.trim();
    }

    private static List<String> toks(String q) {
        List<String> all = new ArrayList<>(), out = new ArrayList<>();
        if (q != null && !q.isEmpty()) for (String s : q.split(" ")) if (!s.isEmpty()) all.add(s);
        for (String s : all) if (!STOP.contains(s)) out.add(s);
        return out.isEmpty() ? all : out;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(' '); sb.append(l.get(i)); }
        return sb.toString();
    }

    private static String aDigitos(String q) {
        String[] a = q.split(" ");
        boolean ch = false;
        for (int i = 0; i < a.length; i++) {
            String d = NUMS.get(a[i]);
            if (d != null) { a[i] = d; ch = true; }
        }
        if (!ch) return q;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(' '); sb.append(a[i]); }
        return sb.toString();
    }

    private static String aLetras(String q) {
        String[] a = q.split(" ");
        boolean ch = false;
        for (int i = 0; i < a.length; i++) {
            String d = LETRAS.get(a[i]);
            if (d != null) { a[i] = d; ch = true; }
        }
        if (!ch) return q;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(' '); sb.append(a[i]); }
        return sb.toString();
    }

    /** "c 5 n" -> "c5n", "a 24" -> "a24": pega las fichas sueltas de 1 caracter. */
    private static String pegar(String q) {
        String[] a = q.split(" ");
        StringBuilder sb = new StringBuilder(), cur = new StringBuilder(a.length > 0 ? a[0] : "");
        for (int i = 1; i < a.length; i++) {
            if (cur.length() == 1 || a[i].length() == 1) cur.append(a[i]);
            else { sb.append(sb.length() > 0 ? " " : "").append(cur); cur = new StringBuilder(a[i]); }
        }
        sb.append(sb.length() > 0 ? " " : "").append(cur);
        return sb.toString();
    }

    private static int lev(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int mn = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1),
                    prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                if (cur[j] < mn) mn = cur[j];
            }
            if (mn > max) return max + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    private static int ptsToken(String t, String[] ws) {
        int best = 0;
        int tol = t.length() >= 7 ? 2 : (t.length() >= 4 ? 1 : 0);
        for (String w : ws) {
            int p = 0;
            if (w.equals(t)) p = 100;
            else if (w.startsWith(t)) p = 85;
            else if (t.length() >= 3 && w.indexOf(t) > 0) p = 55;
            else if (tol > 0 && w.length() >= 3 && lev(t, w, tol) <= tol) p = 60;
            else if (tol > 0 && w.length() > t.length() && lev(t, w.substring(0, t.length()), tol) <= tol) p = 50;
            if (p > best) best = p;
        }
        return best;
    }

    private static int puntaje(String nt, String nk, String q, String qk, List<String> qt) {
        if (nt.isEmpty()) return 0;
        if (nt.equals(q)) return 1000;
        int sc = 0;
        String[] ws = nt.split(" ");
        if (nt.startsWith(q)) sc = 800 - Math.min(nt.length() - q.length(), 100);
        else if ((" " + nt + " ").contains(" " + q + " ")) sc = 650 - Math.min(nt.length() - q.length(), 100);
        if (!qk.isEmpty() && nk != null && !nk.isEmpty()) {
            if (nk.equals(qk)) sc = Math.max(sc, 950);
            else if (nk.startsWith(qk)) sc = Math.max(sc, 780 - Math.min(nk.length() - qk.length(), 100));
            else if ((" " + nk + " ").contains(" " + qk + " ")) sc = Math.max(sc, 640 - Math.min(nk.length() - qk.length(), 100));
        }
        int tot = 0;
        for (String t : qt) {
            int p = ptsToken(t, ws);
            if (p == 0) return sc;
            tot += p;
        }
        int f = Math.round((float) tot / qt.size()) * 3 - Math.min(nt.length(), 80) / 4;
        return Math.max(sc, f);
    }

    // ---------------------------------------------------------------- consulta
    /** "Abrí Gente B por favor" -> "gente b". */
    static String[] interpretar(String raw) {
        String t = norm(raw);
        Matcher m = RE_DIR.matcher(t);
        if (m.find()) t = t.substring(m.end());
        else {
            m = RE_BUS.matcher(t);
            if (m.find()) t = t.substring(m.end());
        }
        t = RE_COLA.matcher(t).replaceAll("");
        t = RE_ARTICULO.matcher(t).replaceAll("");
        String q2 = t.trim();
        t = RE_ART.matcher(t).replaceAll("");
        return new String[]{t.trim(), q2};
    }

    /** Busca en los candidatos lo que se dijo. Nunca devuelve null. */
    static Resultado buscar(List<Item> cands, String texto) {
        String[] qq = interpretar(texto);
        String q = qq[0], q2 = qq[1];
        List<Item> out = new ArrayList<>();
        if (q.isEmpty()) return new Resultado(out, false, "");

        List<String> qs = new ArrayList<>();
        qs.add(q);
        if (!q2.isEmpty() && !q2.equals(q)) qs.add(q2);
        for (int z = qs.size() - 1; z >= 0; z--) {
            String al = aDigitos(qs.get(z));
            if (!qs.contains(al)) qs.add(al);
        }
        for (int z = qs.size() - 1; z >= 0; z--) {
            String al = aLetras(qs.get(z));
            if (!qs.contains(al)) qs.add(al);
            String pg = pegar(al);
            if (!qs.contains(pg)) qs.add(pg);
        }
        for (Item c : cands) {
            if (c.nk == null) c.nk = join(toks(c.nt));
            int best = 0;
            for (String s : qs) {
                List<String> qt = toks(s);
                int sc = puntaje(c.nt, c.nk, s, join(qt), qt);
                if (sc > best) best = sc;
            }
            if (best >= 120) { c.sc = best; out.add(c); }
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                if (b.sc != a.sc) return b.sc - a.sc;
                return a.nt.length() - b.nt.length();
            }
        });
        boolean claro = false;
        if (!out.isEmpty()) {
            Item top = out.get(0), sec = out.size() > 1 ? out.get(1) : null;
            claro = sec == null || top.sc >= 1000 || (top.sc - sec.sc) >= 120 || sec.nt.equals(top.nt);
        }
        return new Resultado(out, claro, q);
    }
}
