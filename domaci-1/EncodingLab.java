import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class EncodingLab {
    public static void main(String[] args) throws Exception {
        var text = "Čačak, Šabac, Žabalj, Ćuprija, Đerdap";
        var file = Path.of("outputs/encoding.txt");
        var windows1250 = Charset.forName("windows-1250");

        Files.writeString(file, text, StandardCharsets.UTF_8);

        var bytes = Files.readAllBytes(file);
        var wrong = new String(bytes, windows1250);

        System.out.println("original: " + text);
        System.out.println("as 1250 : " + wrong);

        for (var c : text.toCharArray()) {
            if (c < 128) {
                continue;
            }

            var u = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            var w = new String(u, windows1250);
            var cps = new StringBuilder();

            w.chars().forEach(x -> cps.append(String.format("U+%04X ", x)));

            System.out.printf("%s U+%04X  UTF-8 = %02X %02X  -> windows-1250: %s (%s)%n",
                    c, (int) c, u[0] & 0xFF, u[1] & 0xFF, w, cps.toString().trim());
        }
    }
}
