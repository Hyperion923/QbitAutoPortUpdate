package dev.hyperionsystems.qbitautoportupdate.debug;

import dev.hyperionsystems.qbitautoportupdate.service.CommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
public class ConsoleCommands {

    private final CommunicationService communicationService;
    @Value("${api.username:admin}")
    private String defaultUsername;

    @Value("${api.password:}")
    private String defaultPassword;

    @ShellMethod(key = "login", value = "Login to the system")
    public String login(
            @ShellOption(help = "username", defaultValue = ShellOption.NULL) String username,
            @ShellOption(help = "password", defaultValue = ShellOption.NULL) String password
    ) {
        String u = (username != null) ? username : defaultUsername;
        String p = (password != null) ? password : defaultPassword;
        communicationService.login(u, p);
        return "Login successful (user=" + u + ")";
    }
    @ShellMethod(key = "updatePort", value = "Update the port")
    public String updatePort(int port){
        this.communicationService.processNewPort(port);
        return "Port updated";
    }


}
