<?php
// Journalise E_DEPRECATED / E_USER_DEPRECATED (y compris ceux silencés par @) dans /var/log/php/deprecations.log,
// une ligne JSON par occurrence, puis rend la main au traitement standard (return false) : aucun effet sur l'application.
// Limite : si l'application installe son propre gestionnaire (Debug::enable, ErrorHandler), il remplace celui-ci.
(function () {
    $log = getenv('DEPRECATION_LOG') ?: '/var/log/php/deprecations.log';
    $previous = null;
    $previous = set_error_handler(function ($type, $message, $file = '', $line = 0) use (&$previous, $log) {
        if ($type === E_DEPRECATED || $type === E_USER_DEPRECATED) {
            $entry = array(
                'ts' => gmdate('c'),
                'type' => $type === E_DEPRECATED ? 'E_DEPRECATED' : 'E_USER_DEPRECATED',
                'message' => $message,
                'file' => $file,
                'line' => $line,
                'sapi' => PHP_SAPI,
                'uri' => isset($_SERVER['REQUEST_URI']) ? $_SERVER['REQUEST_URI'] : null,
                'php' => PHP_VERSION,
            );
            @file_put_contents($log, json_encode($entry, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)."\n", FILE_APPEND | LOCK_EX);
        }
        if (is_callable($previous)) {
            return call_user_func($previous, $type, $message, $file, $line);
        }

        return false;
    });
})();
