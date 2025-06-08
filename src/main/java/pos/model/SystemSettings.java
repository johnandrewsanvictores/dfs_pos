package pos.model;

public class SystemSettings {
    private boolean vatEnabled;
    private int vatRate;
    private String storeName;
    private String storeAddress;
    private String contactEmail;
    private String contactNumber;

    public SystemSettings(boolean vatEnabled, int vatRate, String storeName, String storeAddress, String contactEmail, String contactNumber) {
        this.vatEnabled = vatEnabled;
        this.vatRate = vatRate;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.contactEmail = contactEmail;
        this.contactNumber = contactNumber;
    }

    public boolean isVatEnabled() { return vatEnabled; }
    public int getVatRate() { return vatRate; }
    public String getStoreName() { return storeName; }
    public String getStoreAddress() { return storeAddress; }
    public String getContactEmail() { return contactEmail; }
    public String getContactNumber() { return contactNumber; }
} 