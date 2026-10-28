import java.util.ArrayList;

public class MemLab {
    static int depth;

    static void recurse() {
        depth++;

        recurse();
    }

    public static void main(String[] args) {
        if (args[0].equals("heap")) {
            var list = new ArrayList<byte[]>();

            try {
                while (true) {
                    list.add(new byte[1024 * 1024]);
                }
            } catch (OutOfMemoryError e) {
                var n = list.size();
                list = null;

                System.out.println(n);
            }
        } else {
            try {
                recurse();
            } catch (StackOverflowError e) {
                System.out.println(depth);
            }
        }
    }
}
