<?php
// Couverture PCOV à la demande : n'agit que si l'extension est active (pcov.enabled=1) ET que le fichier
// .claude/state/migration/coverage/enabled existe (posé par .claude/bin/tests.sh --coverage).
// Écrit la couverture de chaque requête / commande dans .claude/state/migration/coverage/raw/ (fusionnée par /coverage).
(function () {
    if (!extension_loaded('pcov') || !ini_get('pcov.enabled')) {
        return;
    }
    $dir = (getenv('APP_ROOT') ?: '/srv/app').'/.claude/state/migration/coverage';
    if (!is_file($dir.'/enabled')) {
        return;
    }
    \pcov\start();
    register_shutdown_function(function () use ($dir) {
        \pcov\stop();
        $data = \pcov\collect(\pcov\inclusive);
        if (!is_dir($dir.'/raw')) {
            @mkdir($dir.'/raw', 0777, true);
        }
        @file_put_contents(sprintf('%s/raw/%s-%s.json', $dir, PHP_SAPI, uniqid('', true)), json_encode($data));
        \pcov\clear();
    });
})();
