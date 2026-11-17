<?php
// /__health : santé du conteneur (Apache + PHP), indépendante de l'application et de sa base.
header('Content-Type: application/json');
echo json_encode(array(
    'status' => 'ok',
    'php' => PHP_VERSION,
    'now' => date('c'),
    'timezone' => date_default_timezone_get(),
    'faketime' => getenv('FAKETIME') ?: null,
    'pdo_sqlite' => extension_loaded('pdo_sqlite'),
    'intl' => extension_loaded('intl'),
    'pcov' => extension_loaded('pcov') && (bool) ini_get('pcov.enabled'),
    'serialize_precision' => ini_get('serialize_precision'),
)), "\n";
