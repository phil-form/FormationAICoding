package be.bstorm.formation.framework.crypto;

class InMemoryCryptoServiceTest extends CryptoServiceContract {

    private final InMemoryCryptoService crypto = new InMemoryCryptoService();

    @Override
    protected CryptoService crypto() {
        return crypto;
    }

    @Override
    protected void rotateKey(SensitiveDataType type) {
        crypto.rotate(type);
    }

    @Override
    protected void setMinDecryptionVersion(SensitiveDataType type, int version) {
        crypto.setMinDecryptionVersion(type, version);
    }
}
