import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Original minor-key waltz, combat variation, and short UI motifs. */
public final class GenerateAudio {
    private static final int RATE = 22050;
    private static final Path OUTPUT = Path.of("fightinggame/resources/audio");
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        write("overture", score(false)); write("battle", score(true));
        for (String name : new String[]{"hover", "click", "attack", "guard", "heal", "magic", "victory", "defeat", "open"}) write(name, effect(name));
    }
    private static double[] score(boolean battle) {
        double beat = 60.0 / (battle ? 126 : 96);
        int bars = 24;
        double[] wave = new double[(int) (bars * 3 * beat * RATE)];
        int[][] chords = {{52, 55, 59}, {48, 52, 55}, {45, 48, 52}, {47, 51, 54}, {52, 55, 59}, {50, 54, 57}, {48, 52, 55}, {47, 51, 54}};
        int[][] melody = {{76, 78, 79, 83, 79, 78}, {76, 74, 72, 76, 79, 76}, {72, 71, 69, 72, 76, 74}, {71, 75, 78, 75, 74, 71},
                {76, 79, 83, 86, 83, 79}, {81, 78, 74, 78, 81, 78}, {79, 76, 72, 76, 74, 72}, {71, 75, 78, 75, 71, 75}};
        for (int bar = 0; bar < bars; bar++) {
            int[] chord = chords[bar % 8]; double start = bar * 3 * beat;
            note(wave, start, beat * 2.5, chord[0] - 12, 0.16, 2);
            for (int b = 1; b < 3; b++) for (int midi : chord) note(wave, start + b * beat, beat * 0.85, midi + 12, 0.055, 0);
            for (int n = 0; n < 6; n++) {
                int midi = melody[bar % 8][n] + (bar >= 16 && n % 3 == 0 ? 12 : 0);
                note(wave, start + n * beat / 2, beat * 0.68, midi, battle ? 0.12 : 0.105, 0);
                if (battle) note(wave, start + n * beat / 2, beat * 0.43, chord[n % 3], 0.09, 1);
            }
            for (int midi : chord) note(wave, start, 2.98 * beat, midi + 12, 0.032, 2);
            if (battle) for (int b = 0; b < 3; b++) {
                note(wave, start + b * beat, 0.14, b == 0 ? 28 : 40, 0.16, 3);
            }
        }
        return wave;
    }
    private static double[] effect(String type) {
        double seconds = type.equals("victory") || type.equals("defeat") ? 1.6 : type.equals("hover") ? 0.07 : type.equals("click") ? 0.13 : 0.65;
        double[] wave = new double[(int) (seconds * RATE)];
        int[] notes = switch (type) {
            case "hover" -> new int[]{88}; case "click" -> new int[]{76, 83}; case "attack" -> new int[]{47, 35};
            case "guard" -> new int[]{59, 66}; case "heal" -> new int[]{76, 80, 83}; case "magic" -> new int[]{71, 78, 83, 90};
            case "victory" -> new int[]{64, 67, 71, 76}; case "defeat" -> new int[]{64, 60, 59, 52}; default -> new int[]{71, 76, 83};
        };
        for (int i = 0; i < notes.length; i++) note(wave, i * seconds / (notes.length + 2), seconds / 2,
                notes[i], type.equals("hover") ? 0.1 : 0.22, type.equals("attack") ? 3 : 0);
        return wave;
    }
    private static void note(double[] wave, double start, double duration, int midi, double volume, int voice) {
        int begin = (int) (start * RATE), length = (int) (duration * RATE);
        double frequency = 440 * Math.pow(2, (midi - 69) / 12.0);
        for (int i = 0; i < length && begin + i < wave.length; i++) {
            double t = (double) i / RATE, p = t * frequency * 2 * Math.PI;
            double envelope = Math.min(1, t / 0.012) * Math.min(1, (duration - t) / 0.045);
            double tone = switch (voice) {
                case 1 -> (Math.sin(p) + 0.3 * Math.sin(2 * p) + 0.12 * Math.sin(3 * p)) * Math.exp(-t * 7);
                case 2 -> (Math.sin(p + 0.012 * Math.sin(5 * t)) + 0.16 * Math.sin(2 * p)) * 0.75;
                case 3 -> Math.sin(p * (1 + 0.4 * Math.exp(-30 * t))) * Math.exp(-t * 20);
                default -> (Math.sin(p) + 0.35 * Math.sin(2.001 * p) * Math.exp(-6 * t) + 0.1 * Math.sin(3 * p)) * Math.exp(-t * 3.5);
            };
            wave[begin + i] += tone * envelope * volume;
        }
    }
    private static void write(String name, double[] wave) throws Exception {
        byte[] pcm = new byte[wave.length * 2]; double peak = 0, power = 0;
        for (int i = 0; i < wave.length; i++) {
            double edge = Math.min(1, Math.min(i, wave.length - 1 - i) / 220.0);
            double sample = Math.tanh(wave[i]) * edge; peak = Math.max(peak, Math.abs(sample)); power += sample * sample;
            short value = (short) Math.round(sample * 32767); pcm[2 * i] = (byte) value; pcm[2 * i + 1] = (byte) (value >> 8);
        }
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (var stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, wave.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, OUTPUT.resolve(name + ".wav").toFile());
        }
        System.out.printf("%s: %.2fs peak=%.3f rms=%.3f%n", name, (double) wave.length / RATE, peak, Math.sqrt(power / wave.length));
    }
}
