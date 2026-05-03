package com.github.fligneul.debtplugin.debt.service.json;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DebtWriterService extends ADebtSerializer {

    private static final Logger LOG = Logger.getInstance(DebtWriterService.class);

    public boolean write(final File jsonAbsolutePathFile, final Object toWrite) {
        final Path jsonPath = jsonAbsolutePathFile.toPath();
        final String jsonAbsolutePathStr = jsonAbsolutePathFile.getAbsolutePath();

        final String json = gson.toJson(toWrite);

        try {
            final File parentFolder = jsonPath.getParent().toFile();
            if (!parentFolder.exists()) {
                parentFolder.mkdirs();
            }
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);

            return true;
        } catch (IOException io) {
            LOG.warn("Failed to write repo debts. path=" + jsonAbsolutePathStr + " message=" + io.getMessage(), io);
            return false;
        }
    }
}
