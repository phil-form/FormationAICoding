package be.bstorm.formation.framework.storage;

/** Contenu déchiffré d'un fichier, prêt à être renvoyé par {@link FileResponses#attachment}. */
public record FileContent(String fileName, String contentType, byte[] content) {

    @Override
    public String toString() {
        return "FileContent[" + contentType + ", " + content.length + " bytes]";
    }
}
