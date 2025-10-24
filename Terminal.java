import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

// Class responsible for parsing down raw input from the user
class Parser {
    String commandName;
    String[] args;
    // Add fields for redirection operators
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
        // Split input into tokens based on whitespace
        List<String> tokens = new ArrayList<>(Arrays.asList(input.trim().split("\\s+")));
        // Merge tokens that are part of the same path for full path commands
        tokens = mergePaths(tokens);

        if (tokens.isEmpty())
            return false;

        commandName = tokens.get(0);

        int redirectIndex = findRedirectionIndex(tokens);

        if (redirectIndex != -1) {
            // Extract arguments before redirection
            this.args = tokens.subList(1, redirectIndex).toArray(new String[0]);

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
            this.args = tokens.subList(1, tokens.size()).toArray(new String[0]);
        }
        return true;
    }
    
    // Helper method to merge path tokens
    private List<String> mergePaths(List<String> tokens) {
        List<String> merged = new ArrayList<>();
        
        for (int i = 0; i < tokens.size(); i++) {
            // get current token
            String token = tokens.get(i);
             // check if it looks like a Windows path
            if (token.matches("^[a-zA-Z]:\\\\.*")) {
                StringBuilder path = new StringBuilder(token);
                
                // Keep mergin while next token looks like a path continuation
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
    // Helper method to find redirection operator index
    private int findRedirectionIndex(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(">") || tokens.get(i).equals(">>")) {
                return i;
            }
        }
        return -1;
    }
    // Getters
    public boolean isRedirected() {return isRedirected;}
    public boolean isAppend() {return isAppend;}
    public String getRedirectFile() {return redirectFile;}
    public String getCommandName() {return commandName;}
    public String[] getArgs() {return args;}
}


// Main class to handle user input and command execution
public class Terminal {

    // Initialize parser and current dir
    Parser parser = new Parser();
    private File currentDirectory = new File(System.getProperty("user.dir"));


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
        //  Use DirectoryStream to list files in the current directory
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDirectory.toPath())) {
            //  Iterate through the directory stream
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
                // Resolve the target path
                Path targetPath = inputPath.isAbsolute() ? inputPath.normalize()
                        : currentDirectory.toPath().resolve(inputPath).normalize();

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
    // Helper method for rmdir *
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
    // Helper method for rmdir dir
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
    // Helper method to check if a directory is empty
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
        // Determine if the path is absolute or relative
        if (filePath.startsWith("/") || filePath.contains(":")) {
            file = new File(filePath);
        } else {
            file = new File(currentDirectory, filePath);
        }
        // Check if parent directory exists
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
    
    // (7) cp cp-r commands
    public void cp(String[] args) {
        if (args.length < 2) {
            System.out.println("cp: missing file operand");
            return;
        }
    
        // Check if the first argument is -r for recursive copy
        boolean isRecursive = args[0].equals("-r");
    
        if (isRecursive) {
            // Logic for recursive copy
            if (args.length != 3) {
                System.out.println("cp: missing destination file operand");
                return;
            }
    
            Path source = Paths.get(args[1]);
            Path destination = Paths.get(args[2]);
    
            // Validate source path for cp -r
            if (!Files.exists(source)) {
                System.out.println("cp: cannot stat '" + args[1] + "': No such file or directory");
                return;
            }
            
            if (!Files.isDirectory(source)) {
                System.out.println("Error: Source path is not a directory. Use 'cp' for files.");
                return;
            }
    
            try {
                // If destination doesn't exist, create it first
                if (!Files.exists(destination)) {
                    Files.createDirectories(destination);
                }
                
                // Determine path as des/source
                Path finalDestination = destination.resolve(source.getFileName());
     
                // Walk the source directory tree and copy each item
                try (Stream<Path> walk = Files.walk(source)) {
                    walk.forEach(sourceItem -> {
                        try {
                            // Calculate the corresponding destination path
                            Path destinationItem = finalDestination.resolve(source.relativize(sourceItem));
                            
                            // Create directory or copy file
                            if (Files.isDirectory(sourceItem)) {
                                Files.createDirectories(destinationItem);
                            } else {
                                Files.copy(sourceItem, destinationItem, 
                                         StandardCopyOption.COPY_ATTRIBUTES, 
                                         StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            System.out.println("cp: error copying '" + sourceItem + "': " + e.getMessage());
                        }
                    });
                }
            } catch (IOException e) {
                System.out.println("cp: cannot copy '" + args[1] + "' to '" + args[2] + "': " + e.getMessage());
            }
        } else {
            // Logic for standard cp (file copy)
            if (args.length != 2) {
                System.out.println("cp: missing destination file operand");
                return;
            }
    
            Path source = Paths.get(args[0]);
            Path destination = Paths.get(args[1]);
    
            // Validate source path for standard cp
            if (!Files.exists(source)) {
                System.out.println("cp: cannot stat '" + args[0] + "': No such file or directory");
                return;
            }
            
            if (Files.isDirectory(source)) {
                System.out.println("cp: -r not specified; omitting directory '" + args[0] + "'");
                return;
            }
    
            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.out.println("cp: cannot create '" + args[1] + "': " + e.getMessage());
            }
        }
    }  

    // (9) rm command
    public void rm(String[] args) {
        if (args.length == 0) {
            System.out.println("rm: missing operand");
            return;
        }
        File file = new File(currentDirectory, args[0]);

        if (!file.exists()) {
            System.out.println("rm: cannot remove '" + args[0] + "': No such file or directory");
            return;
        }
         
        if (file.isDirectory()) {
            System.out.println("rm: cannot remove '" + args[0] + "': Is a directory");
            return;
        }
    
        if (!file.delete()) {
            System.out.println("rm: cannot remove '" + args[0] + "'");
        }
    
    }

    // (10) cat command
    public String cat(String[] args) {
        if (args.length == 0) {
            return "cat: missing operand";
        }
        StringBuilder result = new StringBuilder();


        for (String fileName : args) {
            File file = new File(fileName);

            // Resolve relative paths
            if (!file.isAbsolute()) {
                file = new File(currentDirectory, fileName);
            }

            if (!file.exists()) {
                result.append("cat: ").append(fileName).append(": No such file or directory\n");
                continue;
            }

            if (file.isDirectory()) {
            result.append("cat: ").append(fileName).append(": Is a directory\n");
            continue;
           }
             // Read and append file content
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                   result.append(line).append("\n");
                }
            }catch(IOException e) {
                result.append("cat: ").append(fileName).append(": ").append(e.getMessage()).append("\n");
            }
        }
        return result.toString();
    }

    // (11) wc command
    public String wc(String[] args) {
        if (args.length == 0) {
            return "wc: missing operand";
        }

        File file = new File(currentDirectory, args[0]);
        if (!file.exists()) {
            return "wc: " + args[0] + ": No such file or directory";

        }
        int linesCount = 0;
        int wordsCount = 0;
        int charsCount = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                linesCount++;
                charsCount += line.length() + 1; // +1 for newline character

                if (!line.trim().isEmpty()) {
                    wordsCount += line.trim().split("\\s+").length;
                }
                // Adjust char count for last line not ending with newline
                if (linesCount > 0) {
                    charsCount--;
                }
            }

            return linesCount + " " + wordsCount + " " + charsCount + " " + args[0];

        } catch (IOException e) {

            return "error! cant read file: " + e.getMessage();
        }
    }
        
    // (12) zip command
    public void zip(String[] args) {
        if (args.length < 2) {
            System.out.println("zip: missing operands");
            return;
        }
    
        String zipName;
        boolean recursive = false;
        int start = 0;
    
        if ("-r".equals(args[0])) {
            recursive = true;
            start = 1;
            if (args.length < 3) {
                System.out.println("zip: missing operands");
                return;
                }
            }
        // Name of the zip file
        zipName = args[start];
        boolean addedAnything = false;
        // Create the zip output stream 
        try (FileOutputStream fos = new FileOutputStream(zipName);
            ZipOutputStream zos = new ZipOutputStream(fos)) {
           // Iterate over files to be zipped
            for (int i = start + 1; i < args.length; i++) {
                File f = new File(args[i]);
                // Resolve relative paths
                if (!f.isAbsolute()) f = new File(currentDirectory, args[i]);
                if (!f.exists()) {
                        System.out.println("file not found " + args[i]);
                        continue;
                }
               // Handle directories and files
                if (f.isDirectory()) {
                    if (recursive) {
                            zipDirectory(f, f.getName(), zos);
                            addedAnything = true;
                    } else {
                            System.out.println("zip: " + args[i] + " is a directory ");
                        }
                } else {
                        zipFile(f, zos);
                        addedAnything = true;
                    }
                }
        
            if (!addedAnything) {
                new File(zipName).delete();
                System.out.println("no valid files found to zip, archive not created.");
            }
            
            } catch (IOException e) {
                System.out.println("zip: error creating archive → " + e.getMessage());
            }
        }
        // Helper method to zip a single file
        private void zipFile(File file, ZipOutputStream zos) throws IOException {
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                zos.putNextEntry(new ZipEntry(file.getName()));
                bis.transferTo(zos);
                zos.closeEntry();
            }
        }
        // Helper method to zip a directory recursively
        private void zipDirectory(File dir, String baseName, ZipOutputStream zos) throws IOException {
            File[] files = dir.listFiles();
            if (files == null) return;
    
            for (File f : files) {
                String entryName = baseName + "/" + f.getName();
                if (f.isDirectory()) {
                    zipDirectory(f, entryName, zos);
                } else {
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                        zos.putNextEntry(new ZipEntry(entryName));
                        bis.transferTo(zos);
                        zos.closeEntry();
                    }
                }
            }
        }
        
        // (13) unzip command
        public void unzip(String[] args) {
            if (args.length < 1) {
                System.out.println("unzip: missing operand");
                return;
            }
            // Default destination is current directory
            File destination = currentDirectory;
            String zipName = args[0];
            File zf = new File(zipName);
            if (!zf.isAbsolute())
                zf = new File(currentDirectory, zipName);

            if (!zf.exists() || !zf.isFile()) {
                System.out.println("unzip: archive not found  " + zf.getAbsolutePath());
                return;
            }
            // Check for -d option for destination
            if (args.length >= 2 && "-d".equals(args[1])) {
                if (args.length < 3) {
                    System.out.println("missing destination");
                    return;
                }
                destination = new File(args[2]);
                if (!destination.exists())
                    destination.mkdirs();
            }

            int filesExtracted = 0, dirsCreated = 0;
           // Start extracting the zip file
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zf))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File out = new File(destination, entry.getName());
                    if (entry.isDirectory()) {
                        if (!out.exists())
                            out.mkdirs();
                        dirsCreated++;
                    } else {
                        File parent = out.getParentFile();
                        if (parent != null && !parent.exists())
                            parent.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            zis.transferTo(fos);
                        }
                        filesExtracted++;
                    }
                    zis.closeEntry();
                }
                if (filesExtracted == 0 && dirsCreated == 0) {
                    System.out.println("unzip: no entries found in archive → " + zf.getName());
                }
                
            } catch (IOException e) {
                System.out.println("unzip: error extracting archive → " + e.getMessage());
            }
        }
    
        
    //This method will choose the suitable command method to be called
    public void chooseCommandAction() {
        String command = parser.getCommandName();
        String[] args = parser.getArgs();

        String output = null;
        boolean isOutputCommand = true;

        switch (command) {
            case "pwd":
                output = pwd();
                isOutputCommand = true;
                break;
            case "cd":
                cd(args);
                isOutputCommand = false;
                break;
            case "ls":
                output = ls();
                isOutputCommand = true;
                break;
            case "mkdir":
                mkdir(args);
                isOutputCommand = false;
                break;
            case "rmdir":
                rmdir(args);
                isOutputCommand = false;
                break;
            case "touch":
                touch(args);
                isOutputCommand = false;
                break;
            case "cp":
                cp(args);
                isOutputCommand = false;
                break;
            case "rm":
                rm(args);
                isOutputCommand = false;
                break;
            case "cat":
                output = cat(args);
                isOutputCommand = true;
                break;
            case "wc":
                output = wc(args);
                isOutputCommand = true;
                break;
            case "zip":
                zip(args);
                isOutputCommand = false;
                break;
            case "unzip":
                unzip(args);
                isOutputCommand = false;
                break;
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
    // Helper Method to handle output redirection
    private void redirectOutput(String content, String fileName, boolean append) {
        // resolve the file path based on current directory
        Path filePath = currentDirectory.toPath().resolve(fileName);
        // build a list of file options
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

        System.out.println("Command Line Interpreter (CLI) Started. Type 'exit' to quit.");

        // Main execution loop
        while (true) {
            System.out.print("cli:$ "); // Display prompt to the user

            input = scanner.nextLine();

            // Check for the mandatory 'exit' command
            if (input.trim().equalsIgnoreCase("exit")) {
                System.out.println("CLI terminating!");
                break;
            }
            // Skip processing if the input is empty after trimming
            if (input.trim().isEmpty()) {
                continue;
            }
            if (cli.parser.parse(input)) {
                // Dispatch the command for execution
                cli.chooseCommandAction();
            }
        }

        scanner.close();
    }
     
}