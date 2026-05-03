package com.github.fligneul.debtplugin.debt.service.json;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class DebtReaderService extends ADebtSerializer {
    private static final Logger LOG = Logger.getInstance(DebtReaderService.class);

    public List<DebtItem> readDebts(final File jsonFile) {
        LOG.info("Loading debts from repo file: " + jsonFile.getAbsolutePath());
        try {
            final String content = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);

            final Type type = new TypeToken<List<DebtItem>>() {
            }.getType();

            final List<DebtItem> loaded = gson.fromJson(content, type);

            return loaded == null
                    ? List.of()
                    : loaded;
        } catch (IOException e) {
            LOG.error("Failed loading debts from repo file: " + jsonFile.getAbsolutePath(), e);
            return List.of();
        }
    }
}
