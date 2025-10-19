// class responsible for parsing down raw input from the user
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.Scanner;

class Parser{
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
        
        String[] userInput = input.split("\\s+");
        // set command name 
        commandName = userInput[0];
    
        List<String> inputList = new ArrayList<>();
        for (int i = 1; i < userInput.length; i++) {
            String arg = userInput[i];
            if (arg.equals(">") || arg.equals(">>")) {
                this.isRedirected = true;
                this.isAppend = arg.equals(">>");
                // next arg must be file name
                if (i + 1 < userInput.length) {
                    this.redirectFile = userInput[i + 1];
                    break;

                } else {
                    System.out.println("Error: Missing redirection file name after " + arg);
                    this.commandName = null; // Invalidate command
                    return false;
                }
            }
            // if the arg is not > or >> , it is a normal command argument
            inputList.add(arg);
        }
        
        this.arguments = inputList.toArray(new String[0]);

        
        return true;
    }

    public boolean isRedirected() { return isRedirected; }
    public boolean isAppend() { return isAppend; }
    public String getRedirectFile() { return redirectFile; }
    public String getCommandName() { return commandName; }
    public String[] getArguments() { return arguments; }
}

// main class to handle user input and command execution
public class Terminal {
    Parser parser;
     
    public Terminal() {
        this.parser = new Parser();
    }
    // methods for each command
    public void cp(String[] args) {
        if (args.length < 2) {
            System.out.println("Error: cp requires at least two file operands.");
            return;
        }

        boolean isRecursive = args[0].equals("-r");

        if (isRecursive) {
            // logic for recursive copy
            if (args.length != 3) {
                System.out.println("Error: cp -r requires source and destination directory operands.");
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
                            System.out.println("Error processing " + sourceItem + ": " + e.getMessage());
                        }
                    });
                }
            } catch (IOException e) {
                System.out.println("Fatal error during recursive copy: " + e.getMessage());
            }

        } else {
            // logic for standard 'cp' (file copy)
            if (args.length != 2) {
                System.out.println("Error: cp requires source and destination file operands.");
                return;
            }

            Path source = Paths.get(args[0]);
            Path destination = Paths.get(args[1]);

            // validate source path for standard cp
            if (!Files.exists(source) || Files.isDirectory(source)) {
                System.out.println("Error: Cannot use cp on a directory without the -r flag or File does not exist.");
                return;
            }

            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.out.println("Error copying file: " + e.getMessage());
            }
        }
    } 
    // methods to dispatch the command based on user input
    public void chooseCommandAction(String command, String[] args) {
        String output = null;
        boolean isOutputCommand = true;
        switch (command) {
            case "cp":
                cp(args);
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

        // 2. Main execution loop
        while (true) {
            System.out.print("cli:~$ "); // Display prompt to the user
            
            // Get the entire line of input from the user
            input = scanner.nextLine(); 

            // --- Handle lifecycle commands FIRST ---
            
            // Check for the mandatory 'exit' command
            if (input.trim().equalsIgnoreCase("exit")) {
                System.out.println("CLI terminating. Goodbye!");
                break; // Exit the loop, ending the program
            }
            
            // Skip processing if the input is empty after trimming
            if (input.trim().isEmpty()) {
                continue;
            }

            // 3. Parse the user input
            // The parser extracts commandName, args, and checks for redirection (>, >>)
            if (cli.parser.parse(input)) {
                // 4. Dispatch the command for execution
                cli.chooseCommandAction(cli.parser.getCommandName(), cli.parser.getArguments());
            } 
            // Note: If parsing fails (e.g., redirection syntax error), 
            // the parser should print the error, and the loop continues.
        }
        
        // 5. Close the scanner resource when the program ends
        scanner.close();
    }
}