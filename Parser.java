import java.util.Arrays;

class Parser{
    String commandName;
    String[] arguments;
        
    public boolean parse(String input){
        
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        String[] userInput = input.split("\\s+");
        commandName = userInput[0];
        
         if (userInput.length > 1) {                  
            arguments = Arrays.copyOfRange(userInput, 1, userInput.length);
        } else {
            arguments = new String[0]; 
        }
        return true;   
    }
    public String getCommandName() { return commandName; }
    public String[] getArguments() { return arguments; }
}