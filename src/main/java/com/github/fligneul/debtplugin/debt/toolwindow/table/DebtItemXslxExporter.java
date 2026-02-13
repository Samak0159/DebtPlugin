package com.github.fligneul.debtplugin.debt.toolwindow.table;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class DebtItemXslxExporter extends JButton {
    private static final Logger LOG = Logger.getInstance(DebtItemXslxExporter.class);

    private final Project project;
    private final DebtTable table;
    private final List<DebtItem> allItems;

    public DebtItemXslxExporter(final Project project, final DebtTable table, final List<DebtItem> allItems) {
        super("Export XLSX");
        this.project = project;
        this.table = table;
        this.allItems = allItems;
    }

    @Override
    public void addActionListener(final ActionListener actionListener) {
        final JFileChooser chooser = new JFileChooser(project.getBasePath());
        chooser.setDialogTitle("Export Debt Items to XLSX");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        chooser.setSelectedFile(new File("debt-items.xlsx"));

        int res = chooser.showSaveDialog(SwingUtilities.getWindowAncestor(table));
        if (res != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();
        if (selected == null) return;
        if (!selected.getName().toLowerCase().endsWith(".xlsx")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".xlsx");
        }

        if (selected.exists()) {
            int answer = Messages.showYesNoDialog(
                    project,
                    "File already exists. Do you want to overwrite it?\n" + selected.getAbsolutePath(),
                    "Overwrite File?",
                    null
            );
            if (answer != Messages.YES) {
                return;
            }
        }

        LOG.info("Export started to: " + selected.getAbsolutePath());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Debt Items");

            // Header row
            String[] headers = new String[]{
                    "File", "Line", "Title", "Description", "User", "WantedLevel",
                    "Complexity", "Status", "Priority", "Risk", "TargetVersion", "Comment", "Estimation", "CurrentModule"
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Data rows
            int rowIdx = 1;
            for (DebtItem it : allItems) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(it.getFile());
                row.createCell(c++).setCellValue(it.getLine());
                row.createCell(c++).setCellValue(it.getTitle());
                row.createCell(c++).setCellValue(it.getDescription());
                row.createCell(c++).setCellValue(it.getUsername());
                row.createCell(c++).setCellValue(it.getWantedLevel());
                row.createCell(c++).setCellValue(String.valueOf(it.getComplexity()));
                row.createCell(c++).setCellValue(String.valueOf(it.getStatus()));
                row.createCell(c++).setCellValue(String.valueOf(it.getPriority()));
                row.createCell(c++).setCellValue(String.valueOf(it.getRisk()));
                row.createCell(c++).setCellValue(it.getTargetVersion());
                row.createCell(c++).setCellValue(it.getComment());
                row.createCell(c++).setCellValue(it.getEstimation());
                row.createCell(c).setCellValue(it.getCurrentModule());
            }

            // Autosize
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Ensure parent dir exists
            File parent = selected.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(selected)) {
                workbook.write(fos);
            }

            LOG.info("Export completed. count=" + allItems.size() + " path=" + selected.getAbsolutePath());
            Messages.showInfoMessage(project, "Exported " + allItems.size() + " item(s) to:\n" + selected.getAbsolutePath(), "Export Successful");
        } catch (Exception ex) {
            LOG.error("Export failed. path=" + selected.getAbsolutePath() + " message=" + ex.getMessage(), ex);
            Messages.showErrorDialog(project, "Failed to export XLSX:\n" + ex.getMessage(), "Export Failed");
        }
    }
}
