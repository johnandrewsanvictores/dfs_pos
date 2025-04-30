package pos.view;

import javafx.collections.ObservableList;
import pos.model.CartItem;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.text.TextAlignment;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.printing.PDFPageable;
import java.awt.print.PrinterJob;
import java.awt.print.PrinterAbortException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class ReceiptDialog {
    public static void show(ObservableList<CartItem> cartSnapshot, double paid, double total, String paymentType, double change, Runnable afterPrint) {
        Stage receiptStage = new Stage();
        receiptStage.initModality(Modality.APPLICATION_MODAL);
        receiptStage.setTitle("Receipt");
        VBox receiptBox = new VBox(6);
        receiptBox.setPadding(new Insets(20));
        receiptBox.setAlignment(Pos.TOP_CENTER);
        receiptBox.setStyle("-fx-background-color: white;");
        Label storeName = new Label("Dream Fashion Shop");
        storeName.setFont(new Font(20));
        storeName.setStyle("-fx-font-weight: bold;");
        Label address = new Label("123 Main St, City, Country");
        address.setFont(new Font(12));
        Label cashier = new Label("Cashier: John Andrew San Victores");
        cashier.setFont(new Font(12));
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Label dateTime = new Label("Date: " + LocalDateTime.now().format(dtf));
        dateTime.setFont(new Font(12));
        Separator sep1 = new Separator();
        Label itemsLabel = new Label("Items:");
        itemsLabel.setFont(new Font(14));
        VBox itemsBox = new VBox(2);
        itemsBox.setPadding(new Insets(5,0,5,0));
        for (CartItem item : cartSnapshot) {
            String line = String.format("%s x%d  PHP %.2f", item.getProduct().getSku(), item.getQuantity(), item.getSubtotal());
            Label l = new Label(line);
            l.setFont(new Font(13));
            itemsBox.getChildren().add(l);
        }
        Separator sep2 = new Separator();
        Label subtotal = new Label(String.format("Subtotal: PHP %.2f", total));
        subtotal.setFont(new Font(13));
        Label discount = new Label("Discount: 0%");
        discount.setFont(new Font(13));
        Label totalLbl = new Label(String.format("Total: PHP %.2f", total));
        totalLbl.setFont(new Font(15));
        totalLbl.setStyle("-fx-font-weight: bold;");
        Label paidLbl = new Label(String.format("Amount Paid: PHP %.2f", paid));
        paidLbl.setFont(new Font(13));
        Label paymentTypeLbl = new Label("Payment Method: " + paymentType);
        paymentTypeLbl.setFont(new Font(13));
        Label changeLbl = new Label(String.format("Change: PHP %.2f", change));
        changeLbl.setFont(new Font(13));
        Separator sep3 = new Separator();
        Label thankYou = new Label("Thank you for shopping with us!");
        thankYou.setFont(new Font(14));
        thankYou.setTextAlignment(TextAlignment.CENTER);
        Button printBtn = new Button("Print Receipt");
        printBtn.setOnAction(e -> {
            try {
                File pdfFile = generateReceiptPDF(cartSnapshot, paid, total, paymentType, change);
                printPDF(pdfFile);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            receiptStage.close();
            if (afterPrint != null) afterPrint.run();
        });
        receiptBox.getChildren().addAll(
            storeName, address, cashier, dateTime, sep1,
            itemsLabel, itemsBox, sep2,
            subtotal, discount, totalLbl, paidLbl, paymentTypeLbl, changeLbl, sep3, thankYou, printBtn
        );
        Scene scene = new Scene(receiptBox);
        receiptStage.setScene(scene);
        receiptStage.setWidth(350);
        receiptStage.setHeight(500);
        receiptStage.showAndWait();
    }

    private static File generateReceiptPDF(ObservableList<CartItem> cartSnapshot, double paid, double total, String paymentType, double change) throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A6);
        doc.addPage(page);
        PDPageContentStream content = new PDPageContentStream(doc, page);
        float y = page.getMediaBox().getHeight() - 30;
        float left = 30;
        content.setFont(PDType1Font.HELVETICA_BOLD, 14);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Dream Fashion Shop");
        content.endText();
        y -= 18;
        content.setFont(PDType1Font.HELVETICA, 8);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("123 Main St, City, Country");
        content.endText();
        y -= 12;
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Cashier: John Andrew San Victores");
        content.endText();
        y -= 12;
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Date: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        content.endText();
        y -= 18;
        content.setFont(PDType1Font.HELVETICA_BOLD, 10);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Items:");
        content.endText();
        y -= 14;
        content.setFont(PDType1Font.HELVETICA, 9);
        for (CartItem item : cartSnapshot) {
            String line = String.format("%s x%d  PHP %.2f", item.getProduct().getSku(), item.getQuantity(), item.getSubtotal());
            content.beginText();
            content.newLineAtOffset(left, y);
            content.showText(line);
            content.endText();
            y -= 12;
        }
        y -= 6;
        content.setFont(PDType1Font.HELVETICA, 9);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Subtotal: PHP " + String.format("%.2f", total));
        content.endText();
        y -= 12;
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Discount: 0%");
        content.endText();
        y -= 12;
        content.setFont(PDType1Font.HELVETICA_BOLD, 10);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Total: PHP " + String.format("%.2f", total));
        content.endText();
        y -= 14;
        content.setFont(PDType1Font.HELVETICA, 9);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Amount Paid: PHP " + String.format("%.2f", paid));
        content.endText();
        y -= 12;
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Payment Method: " + paymentType);
        content.endText();
        y -= 12;
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Change: PHP " + String.format("%.2f", change));
        content.endText();
        y -= 18;
        content.setFont(PDType1Font.HELVETICA_BOLD, 10);
        content.beginText();
        content.newLineAtOffset(left, y);
        content.showText("Thank you for shopping with us!");
        content.endText();
        content.close();
        File tempFile = File.createTempFile("receipt", ".pdf");
        tempFile.deleteOnExit();
        doc.save(tempFile);
        doc.close();
        return tempFile;
    }

    private static void printPDF(File pdfFile) throws IOException {
        PDDocument document = PDDocument.load(pdfFile);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPageable(new PDFPageable(document));
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterAbortException ex) {
                System.out.println("Print job was cancelled or aborted.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        document.close();
    }
} 