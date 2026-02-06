package testapp;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

public class DebtGeneratorApp {
    public static void main(String[] args) {
        new DebtGeneratorApp();
    }

    public DebtGeneratorApp() {
        int nbDebtExpected = 200;

        final Random random = new Random();

        var debts = new ArrayList<DebtItem>();
        for (int i = 0; i < nbDebtExpected; i++) {
            final Complexity complexity = Complexity.values()[random.nextInt(0, Complexity.values().length)];
            final Status status = Status.values()[random.nextInt(0, Status.values().length)];
            final Priority priority = Priority.values()[random.nextInt(0, Priority.values().length)];
            final Risk risk = Risk.values()[random.nextInt(0, Risk.values().length)];


            final DebtItem item = new DebtItem().toBuilder()
                    .withId(UUID.randomUUID().toString())
                    .withFile("")
                    .withLine(1)
                    .withTitle(generateRandomString(10))
                    .withDescription(generateRandomString(100))
                    .withUsername("Blop")
                    .withWantedLevel(random.nextInt(1, 6))
                    .withComplexity(complexity)
                    .withStatus(status)
                    .withPriority(priority)
                    .withRisk(risk)
                    .withTargetVersion("")
                    .withComment(generateRandomString(100))
                    .withEstimation(0)
                    .withCurrentModule(null)
                    .withLinks(null)
                    .build();

            debts.add(item);
        }

        // Todo should use a WriterService but i doesn't exist yet.
        var gson = new GsonBuilder()
                .registerTypeAdapter(DebtItem.class, new DebtService.DebtItemDeserializer())
                .setPrettyPrinting()
                .create();

        final String json = gson.toJson(debts);

        final Path jsonPath;
        try {
            jsonPath = Files.createTempFile("debts", ".json");
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Done jsonPath = " + jsonPath);
    }

    private String generateRandomString(final int stringLength) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'

        final Random random = new Random();
        final StringBuilder buffer = new StringBuilder(stringLength);

        for (int i = 0; i < stringLength; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }

        return buffer.toString();
    }
}
