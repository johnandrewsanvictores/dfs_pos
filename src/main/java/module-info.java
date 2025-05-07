module com.mycompany.pos_fx {
    requires javafx.controls;
    exports com.mycompany.pos_fx;
    requires org.kordamp.ikonli.materialdesign;
    requires org.kordamp.ikonli.javafx;
    requires org.apache.pdfbox;
    requires java.desktop;
    requires java.sql;
    requires mysql.connector.j;
    requires transitive jbcrypt;
    requires jasperreports;
}
