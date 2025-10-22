import java.nio.file.*;
import java.util.stream.Stream;
import java.util.*;
import java.io.*;
 

// class responsible for parsing down raw input from the user
class Parser {
    String commandName;
    String[] arguments;
    // add fields for redirection
    private boolean isRedirected = false;
    private boolean isAppend = false;
    private String redirectFile = null;

    public boolean parse(String input) {
    this.isRedirected = false;
    this.isAppend = false;
    this.redirectFile = null;
    
    if (input == null || input.trim().isEmpty()) {
        return false;
    }

    List<String> tokens = new ArrayList<>(Arrays.asList(input.trim().split("\\s+")));
    
    tokens = mergePaths(tokens);
    
    if (tokens.isEmpty()) return false;

    commandName = tokens.get(0);
    
    int redirectIndex = findRedirectionIndex(tokens);
    
    if (redirectIndex != -1) {
        // Extract arguments before redirection
        this.arguments = tokens.subList(1, redirectIndex).toArray(new String[0]);
        
        // Extract redirection info
        this.isRedirected = true;
        this.isAppend = tokens.get(redirectIndex).equals(">>");
        
        if (redirectIndex + 1 < tokens.size()) {
            this.redirectFile = tokens.get(redirectIndex + 1);
        } else {
            System.out.println("Error: Missing redirection file name");
            this.commandName = null;
            return false;
        }
    } else {
        // No redirection, all tokens after command are arguments
        this.arguments = tokens.subList(1, tokens.size()).toArray(new String[0]);
    }
    return true;
    }
    
    private List<String> mergePaths(List<String> tokens) {
        List<String> merged = new ArrayList<>();
        
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            
            if (token.matches("^[a-zA-Z]:\\\\.*")) {
                StringBuilder path = new StringBuilder(token);
                
                // Keep merging while next token looks like a path continuation
                while (i + 1 < tokens.size() && 
                       !tokens.get(i + 1).equals(">") && 
                       !tokens.get(i + 1).equals(">>") &&
                       !tokens.get(i + 1).matches("^[a-zA-Z]:\\\\.*")) {
                    path.append(" ").append(tokens.get(++i));
                }
                merged.add(path.toString());
            } else {
                merged.add(token);
            }
        }
        return merged;
    }
    
    private int findRedirectionIndex(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(">") || tokens.get(i).equals(">>")) {
                return i;
            }
        }
        return -1;
    }
    
    public boolean isRedirected() {return isRedirected;}
    public boolean isAppend() {return isAppend;}
    public String getRedirectFile() {return redirectFile;}
    public String getCommandName() {return commandName;}
    public String[] getArguments() {return arguments;}
}


// main class to handle user input and command execution
public class Terminal{
    Parser parser = new Parser();
    private File currentDirectory = new File(System.getProperty("user.dir"));

    // methods for each command:

    // (1) pwd command
    public String pwd() {
        return currentDirectory.toString();
    }
    // (2) cd command
    public void cd(String[] args) {
        // case 1: cd
        if (args.length == 0) {
            currentDirectory = new File(System.getProperty("user.home"));
        }
    
        // case 2: cd ..
        else if (args[0].equals("..")) {
            File parent = currentDirectory.getParentFile();
            if (parent != null) {
                currentDirectory = parent;
            } 
        }
        // case 3: cd path
        else {
            Path newPath = Paths.get(args[0]);
            if (!newPath.isAbsolute()) {
                newPath = currentDirectory.toPath().resolve(newPath);
            }
            newPath = newPath.normalize();
            if (Files.isDirectory(newPath)) {
                currentDirectory = newPath.toFile();
            } else {
                System.out.println("cd: " + args[0] + ": No such directory");
            }
        }
    }

    // (3) ls command
    public String ls() {
    StringBuilder result = new StringBuilder();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDirectory.toPath())) {

        List<String> names = new ArrayList<>();
        for (Path path : stream) {
            names.add(path.getFileName().toString());
        }

        Collections.sort(names);

        for (String name : names) {
             result.append(name).append("\n"); 
        }
    } catch (IOException e) {
        return "Error: cannot list directory: " + e.getMessage();
    }
    return result.toString().trim();

    }
    
    // (4) mkdir command
    public void mkdir(String[] args) {
       if (args.length == 0) {
        System.out.println("mkdir: missing operand");
        return;
    }
    for (String arg : args) {
        try {
            Path inputPath = Paths.get(arg);
            Path targetPath = inputPath.isAbsolute() ? 
                              inputPath.normalize() : 
                              currentDirectory.toPath().resolve(inputPath).normalize();

            if (Files.exists(targetPath)) {
                System.out.println("mkdir: cannot create directory '" + arg + "': File exists");
                continue;
            }

            Files.createDirectories(targetPath);

        } catch (Exception e) {
            System.out.println("mkdir: cannot create directory '" + arg + "'");
          }
       }
    }
    // (5) rmdir command
    public void rmdir(String[] args) {
        if (args.length != 1) {
            System.out.println("rmdir: missing operand");
            return;
        }

        if ("*".equals(args[0])) {
            removeAllEmptyDirectories();
        } else {
            removeSingleDirectory(args[0]);
        }
    }

    private void removeAllEmptyDirectories() {
        File[] files = currentDirectory.listFiles();

        if (files == null) {
            System.out.println(" Cannot read current directory");
            return;
        }
        for (File file : files) {
            if (file.isDirectory() && isDirectoryEmpty(file)) {
                file.delete();
            }
        }
    }

    private void removeSingleDirectory(String path) {
        File dir;
        if (path.startsWith("/") || path.contains(":")) {
            dir = new File(path);
        } else {
            dir = new File(currentDirectory, path);
        }

        if (!dir.exists()) {
            System.out.println("rmdir: failed to remove '" + path + "': No such file or directory");
            return;
        }

        if (!dir.isDirectory()) {
            System.out.println("rmdir: failed to remove '" + path + "': Not a directory");
            return;
        }

        if (!isDirectoryEmpty(dir)) {
            System.out.println("rmdir: failed to remove '" + path + "': Directory is not empty");
            return;
        }

        if (!dir.delete()) {
            System.out.println("rmdir: failed to remove '" + path + "'");
        }  
        
    }

    private boolean isDirectoryEmpty(File directory) {
        if (!directory.isDirectory()) return false;
        String[] contents = directory.list();
        return contents != null && contents.length == 0;
    }


    // (6) touch command
    public void touch(String[] args) {
        if (args.length != 1) {
            System.out.println("touch: missing file operand");
            return;
        }

        String filePath = args[0];
        File file;

        if (filePath.startsWith("/") || filePath.contains(":")) {
            file = new File(filePath);
        } else {
            file = new File(currentDirectory, filePath);
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            System.out.println("Error: Parent directory does not exist: " + parent.getAbsolutePath());
            return;
        }

        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println("touch: cannot touch '" + args[0] + "'");
        }
    }

    // (7,8) cp and cp -r command
    public void cp(String[] args) {
        if (args.length < 2) {
            System.out.println("cp: missing file operand");
            return;
        }

        boolean isRecursive = args[0].equals("-r");

        if (isRecursive) {
            // logic for recursive copy
            if (args.length != 3) {
                System.out.println("cp: missing destination file operand");
                return;
            }

            Path source = Paths.get(args[1]);
            Path destination = Paths.get(args[2]);

            // validate source path for cp -r
            if (!Files.isDirectory(source)) {
                System.out.println("Error: Source path is not a directory. Use 'cp' for files.");
                return;
            }

            try {
                // create the destination directory  
                Files.createDirectories(destination);

                //  walk the source directory tree and copy each item
                try (Stream<Path> walk = Files.walk(source)) {
                    walk.forEach(sourceItem -> {
                        try {
                            // calculate the corresponding destination path
                            Path destinationItem = destination.resolve(source.relativize(sourceItem));

                            // copy the item (file or directory)
                            Files.copy(sourceItem, destinationItem,
                                    StandardCopyOption.COPY_ATTRIBUTES,
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            System.out.println("cp: error copying '" + sourceItem + "'");
                        }
                    });
                }
            } catch (IOException e) {
                System.out.println("cp: cannot create directory '" + args[2] + "'");
            }

        } else {
            // logic for standard 'cp' (file copy)
            if (args.length != 2) {
                System.out.println("cp: missing destination file operand");
                return;
            }

            Path source = Paths.get(args[0]);
            Path destination = Paths.get(args[1]);

            // validate source path for standard cp
            if (!Files.exists(source) || Files.isDirectory(source)) {
                System.out.println("Error: Cannot use cp on a directory without the -r flag .");
                return;
            }

            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.out.println("cp: cannot create '" + args[1] + "'");
            }
        }
    }
    // (9) rm command
    public void rm(String[] args) {
        if (args.length == 0) {
            System.out.println("rm: missing operand");
            return;
        }

        for (String arg : args) {
            File file = new File(currentDirectory,arg);

            if (!file.exists()) {
                System.out.println("rm: cannot remove '" + arg + "': No such file or directory");
                continue;
            }

            if (!deleteRecursively(file)) {
                System.out.println("rm: cannot remove '" + arg + "'");
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
    // (10) cat command
    public void cat(String[] args) {
        if (args.length == 0) {
            System.out.println("cat: missing operand");
            return;
        }

        for (String fileName : args) {
            File file = new File(currentDirectory, fileName);

            if (!file.exists()) {
                System.out.println("cat: " + fileName + ": No such file or directory");
                continue;
            }

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }catch(IOException e) {
                System.out.println("cat: " + fileName + ": " + e.getMessage());
            }
        }
    }

    // (11) wc command

    // (12) zip command

    // (13) unzip command


    //This method will choose the suitable command method to be called
    public void chooseCommandAction() {
        String command = parser.getCommandName();
        String[] args = parser.getArguments();

        String output = null;
        boolean isOutputCommand = true;
        
        switch (command) {
            case "pwd":
                output = pwd();
                isOutputCommand = true; break;
            case "cd":
                cd(args);
                isOutputCommand = false; break;
            case "ls":
                output = ls();
                isOutputCommand = true;  break;
            case "mkdir":
                mkdir(args);
                isOutputCommand = false; break;
            case "rmdir":
                rmdir(args); 
                isOutputCommand = false; break;
            case "touch": 
                touch(args); 
                isOutputCommand = false; break;
            case "cp":
                cp(args);
                isOutputCommand = false; break;
            case "rm":
                rm(args);
                isOutputCommand = false; break;
            case "cat":
                cat(args);
                isOutputCommand = false; break;
            case "wc":
                // wc(args);
                isOutputCommand = false; break;
            case "zip":
                // zip(args);
                isOutputCommand = false; break;
            case "unzip":
                // unzip(args);
                isOutputCommand = false; break;
            default:
                System.out.println("Error: Unknown command " + command);
                return;
        }
        if (isOutputCommand && output != null) {
            if (parser.isRedirected()) {
                // use the data extracted by the parser to redirect the output
                redirectOutput(output, parser.getRedirectFile(), parser.isAppend());
            } else {
                System.out.println(output);

            }
        }
    }
    private void redirectOutput(String content, String fileName, boolean append) {
        Path filePath = Paths.get(fileName);
        ArrayList<StandardOpenOption> options = new ArrayList<>();
        options.add(StandardOpenOption.CREATE);
        options.add(StandardOpenOption.WRITE);
        if (append) {
            // >> add to the end of the file
            options.add(StandardOpenOption.APPEND);
        } else {
            // > overwrite the file
            options.add(StandardOpenOption.TRUNCATE_EXISTING);
        }
        try {
            // writes the content to the file with the specified options
            Files.writeString(filePath, content + "\n", options.toArray(new StandardOpenOption[0]));
        } catch (IOException e) {
            System.out.println("Error redirecting output to file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Terminal cli = new Terminal();
        Scanner scanner = new Scanner(System.in);
        String input;

        System.out.println("--- Command Line Interpreter (CLI) Started ---");
        System.out.println("Enter commands. Type 'exit' to terminate.");

        // Main execution loop
        while (true) {
            System.out.print("cli:~$ "); // Display prompt to the user
            
            input = scanner.nextLine(); 

             
            // Check for the mandatory 'exit' command
            if (input.trim().equalsIgnoreCase("exit")) {
                System.out.println("CLI terminating. Goodbye!");
                break; // Exit the loop, ending the program
            }
            
            // Skip processing if the input is empty after trimming
            if (input.trim().isEmpty()) {
                continue;
            }

            // parser extracts commandName, args, and checks for redirection
            if (cli.parser.parse(input)) {
                // Dispatch the command for execution
                cli.chooseCommandAction();
            } 
         }
        
        scanner.close();
    }
}