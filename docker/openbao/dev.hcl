# OpenBao — complément de configuration du mode dev (le listener et le stockage en mémoire sont fournis par -dev).
# OpenBao 2.x n'autorise pas l'activation d'un audit par l'API : il est déclaré ici (comme en prod).
audit "file" "stdout" {
  description = "Audit dev (sortie standard du conteneur)"
  options {
    file_path = "stdout"
  }
}
