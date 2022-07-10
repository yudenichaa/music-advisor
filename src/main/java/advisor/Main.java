package advisor;

import org.apache.commons.cli.*;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException, ParseException {
        Option access = new Option("a", "access", true, "Auth server path");
        Option resource = new Option("r", "resource", true, "API server path");
        Options options = new Options();
        options.addOption(access).addOption(resource);
        CommandLineParser commandLineParser = new DefaultParser();
        CommandLine cmd = commandLineParser.parse(options, args);
        String authPath = cmd.getOptionValue("access", "https://accounts.spotify.com");
        String apiPath = cmd.getOptionValue("resource", "https://api.spotify.com");
        Advisor.run(authPath, apiPath);
    }
}
