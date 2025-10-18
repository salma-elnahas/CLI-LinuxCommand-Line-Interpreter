import java.io.*;
import java.util.Scanner;

public class Terminal {
    private Parser parser = new Parser();
    private File currentDirectory = new File("C:\\TestRm2");


    public static void main(String[] args) {
        Terminal terminal = new Terminal();
        terminal.run();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Mini Terminal (type 'exit' to quit)");

        while (true) {
            System.out.print("> "); // looks like a real terminal
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit")) break;

            parser.parse(input);
            String command = parser.getCommandName();
            String[] arguments = parser.getArguments();

            if (command == null) continue;

            switch (command) {
                case "rm":
                    rm(arguments);
                    break;

                case "cat":
                    try {
                        cat(arguments);
                    } catch (IOException e) {
                        System.out.println("Error reading file: " + e.getMessage());
                    }
                    break;
                case "mkdir":
                mkdir(arguments);
                break;

                default:
                    System.out.println("Unknown command: " + command);
            }
        }

        scanner.close();
    }

    public void mkdir(String[] args) {
    if (args.length == 0) {
        System.out.println("Error: mkdir requires at least one argument");
        return;
    }

    for (String arg : args) {
        if (arg.isEmpty()) {
            System.out.println("Error: Empty directory name");
            continue;
        }

        File dir;
        if (arg.startsWith("/") || arg.contains(":")) {
            dir = new File(arg);
        } else {
            dir = new File(currentDirectory, arg);
        }

        if (dir.exists() && dir.isFile()) {
            System.out.println("Error: A file with the same name exists: " + arg);
            continue;
        }

        if (dir.exists()) {
            System.out.println("Directory already exists: " + dir.getAbsolutePath());
            continue;
        }

        if (dir.mkdirs()) {
            System.out.println("Directory created: " + dir.getAbsolutePath());
        } else {
            System.out.println("Failed to create directory: " + dir.getAbsolutePath());
            File parent = dir.getParentFile();
            if (parent != null && !parent.exists()) {
                System.out.println("  Parent directory does not exist: " + parent.getAbsolutePath());
            } else if (parent != null && !parent.canWrite()) {
                System.out.println("  No write permission in: " + parent.getAbsolutePath());
            }
        }
    }
}

    public void rm(String[] args) 
    {
        if (args.length == 0) {
            System.out.println("Error! remove command needs a directory");
            return;
        }

        for (String arg : args) {
            File file = new File(currentDirectory,arg);

            if (!file.exists()) {
                System.out.println("Error! " + arg + " file/directory doesn't exist");
                continue;
            }

            if (deleteRecursively(file)) {
                System.out.println("successfully deleted: " + arg);
            } else {
                System.out.println("Error! cant delete  " + arg);
            }
        }
    }

    private boolean deleteRecursively(File file) {
    if (file.isDirectory()) {
        File[] contents = file.listFiles();

        if (contents != null) {
            for (File f : contents) {
                if (!deleteRecursively(f)) {
                    return false;
                }
            }
        }
       

    }
    
    return file.delete();
}

    public void cat(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Error! cat requires at least one file.");
            return;
        }

        for (String fileName : args) {
            File file = new File(currentDirectory, fileName);

            if (!file.exists()) {
                System.out.println("File " + fileName + " doesn't exist.");
                continue;
            }

           

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }
        }
    }
}
