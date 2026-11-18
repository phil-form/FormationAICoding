package be.bstorm.formation.framework.crypto;

class InMemoryPasswordPepperTest extends PasswordPepperContract {

    private final InMemoryPasswordPepper pepper = new InMemoryPasswordPepper();

    @Override
    protected PasswordPepper pepper() {
        return pepper;
    }

    @Override
    protected void rotateKey() {
        pepper.rotate();
    }
}
