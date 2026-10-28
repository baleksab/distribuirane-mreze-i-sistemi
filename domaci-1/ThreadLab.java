public class ThreadLab {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        for (int i = 1; i <= 3; i++) {
            new Thread(ThreadLab::radi, "worker-" + i).start();
        }
        System.out.println("PID " + ProcessHandle.current().pid());
        try (var server = new java.net.ServerSocket(port)) {
            System.out.println("Slušam na portu " + port);
            while (true)
                server.accept().close();
        }
    }

    static void radi() {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            // nit se završava
        }
    }
}