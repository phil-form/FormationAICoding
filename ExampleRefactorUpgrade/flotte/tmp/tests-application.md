# Tests de caractérisation — application flotte

> **Document unique de suivi** de la mise en place des tests de caractérisation de toute l'application flotte
> (5 groupes : 4 contrôleurs et les commandes console). Il contient le gabarit commun, toutes les fiches `BH-NNN`,
> leurs exemples complets, les points à trancher, le journal d'implémentation et les blocages ; son bloc
> « Suivi d'avancement » est régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`).
>
> Valeurs « observées » : application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), 2026-11-17. Mise à jour
> après chaque implémentation : `.claude/reference/conventions.md` § 5.6. Inventaire préalable des routes et
> commandes : `tmp/routes-et-commandes.md`.

<!-- tracking:start -->
## Suivi d'avancement (généré)

> Régénéré par `.claude/bin/test-tracking.py` (fin de chaque `.claude/bin/tests.sh`) — 2026-11-17 14:08. Ne pas éditer ce bloc : pour bloquer une fiche, ajouter « - BH-NNN : ⏸ raison » dans la section « Blocages ».

**209/209 terminés (100 %)** `██████████████████████████████`

✅ 209 terminés · 🟡 0 incomplets · ✍️ 0 écrits · 🔴 0 rouges réf. · ⏸ 0 bloqués · ⬜ 0 à écrire

| Groupe | Route | Écrites | Terminées | Avancement |
|---|---|---|---|---|
| **`VehicleController`** | | **40/40** | **40/40** | `██████████` 100 % |
| | `homepage` | 4/4 | 4/4 | `██████████` 100 % |
| | `vehicle_index` | 19/19 | 19/19 | `██████████` 100 % |
| | `vehicle_show` | 17/17 | 17/17 | `██████████` 100 % |
| **`ReservationController`** | | **69/69** | **69/69** | `██████████` 100 % |
| | `reservation_new` | 39/39 | 39/39 | `██████████` 100 % |
| | `reservation_return` | 30/30 | 30/30 | `██████████` 100 % |
| **`ApiController`** | | **25/25** | **25/25** | `██████████` 100 % |
| | `api_vehicles` | 25/25 | 25/25 | `██████████` 100 % |
| **`AdminController`** | | **38/38** | **38/38** | `██████████` 100 % |
| | `admin_vehicle_new` | 38/38 | 38/38 | `██████████` 100 % |
| **Commandes console** | | **37/37** | **37/37** | `██████████` 100 % |
| | `fleet:report` | 26/26 | 26/26 | `██████████` 100 % |
| | `fleet:demo:load` | 11/11 | 11/11 | `██████████` 100 % |

<details><summary>Détail par fiche</summary>

| Fiche | Route | Fiche BH | Tests | Golden | Réf | App | Diff | Statut |
|---|---|---|---|---|---|---|---|---|
| BH-001 | `homepage` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-002 | `homepage` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-003 | `homepage` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-004 | `homepage` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-010 | `vehicle_index` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-011 | `vehicle_index` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-012 | `vehicle_index` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-013 | `vehicle_index` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-014 | `vehicle_index` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-015 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-016 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-017 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-018 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-019 | `vehicle_index` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-020 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-021 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-022 | `vehicle_index` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-023 | `vehicle_index` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-024 | `vehicle_index` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-025 | `vehicle_index` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-026 | `vehicle_index` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-027 | `vehicle_index` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-028 | `vehicle_index` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-040 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-041 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-042 | `vehicle_show` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-043 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-044 | `vehicle_show` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-045 | `vehicle_show` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-046 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-047 | `vehicle_show` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-048 | `vehicle_show` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-049 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-050 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-051 | `vehicle_show` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-052 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-053 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-054 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-055 | `vehicle_show` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-056 | `vehicle_show` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-100 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-101 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-102 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-103 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-104 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-105 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-106 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-107 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-108 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-109 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-110 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-111 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-112 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-113 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-114 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-115 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-116 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-117 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-118 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-119 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-120 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-121 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-122 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-123 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-124 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-125 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-126 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-127 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-128 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-129 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-130 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-131 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-132 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-133 | `reservation_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-134 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-135 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-136 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-137 | `reservation_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-138 | `reservation_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-150 | `reservation_return` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-151 | `reservation_return` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-152 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-153 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-154 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-155 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-156 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-157 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-158 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-159 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-160 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-161 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-162 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-163 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-164 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-165 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-166 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-167 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-168 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-169 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-170 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-171 | `reservation_return` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-172 | `reservation_return` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-173 | `reservation_return` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-174 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-175 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-176 | `reservation_return` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-177 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-178 | `reservation_return` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-179 | `reservation_return` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-200 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-201 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-202 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-203 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-204 | `api_vehicles` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-205 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-206 | `api_vehicles` | — | 3 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-207 | `api_vehicles` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-208 | `api_vehicles` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-209 | `api_vehicles` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-210 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-211 | `api_vehicles` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-212 | `api_vehicles` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-213 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-214 | `api_vehicles` | — | 1 | ✔ 1 | ✔ | ✔ | — | ✅ terminé |
| BH-215 | `api_vehicles` | — | 3 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-216 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-217 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-218 | `api_vehicles` | — | 1 | ✔ 1 | ✔ | ✔ | — | ✅ terminé |
| BH-219 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-220 | `api_vehicles` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-221 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-222 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-223 | `api_vehicles` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-224 | `api_vehicles` | — | 2 | ✔ 1 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-300 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-301 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-302 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-303 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-304 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-305 | `admin_vehicle_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-306 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-307 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-308 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-309 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-310 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-311 | `admin_vehicle_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-312 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-313 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-314 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-315 | `admin_vehicle_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-316 | `admin_vehicle_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-317 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-318 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-319 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-320 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-321 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-322 | `admin_vehicle_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-323 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-324 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-325 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-326 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-327 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-328 | `admin_vehicle_new` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-329 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-330 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-331 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-332 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-333 | `admin_vehicle_new` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-334 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-335 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-336 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-337 | `admin_vehicle_new` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-400 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-401 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-402 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-403 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-404 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-405 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-406 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-407 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-408 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-409 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-410 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-411 | `fleet:report` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-412 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-413 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-414 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-415 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-416 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-417 | `fleet:report` | — | 3 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-418 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-419 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-420 | `fleet:report` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-421 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-422 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-423 | `fleet:report` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-424 | `fleet:report` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-425 | `fleet:report` | — | 2 | ✔ 2 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-450 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-451 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-452 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-453 | `fleet:demo:load` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-454 | `fleet:demo:load` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-455 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-456 | `fleet:demo:load` | — | 1 | — | ✔ | ✔ | — | ✅ terminé |
| BH-457 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-458 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |
| BH-459 | `fleet:demo:load` | — | 2 | ✔ 2 | ✔ | ✔ | ✔ | ✅ terminé |
| BH-460 | `fleet:demo:load` | — | 2 | — | ✔ | ✔ | ✔ | ✅ terminé |

Légende — Fiche BH : décrite dans `docs/migration/behaviors/` · Tests : méthodes `bhNNN_*` · Golden : ✔ présents / ✘ manquants / — aucun · Réf / App : dernier run sur `app-ref` / `app` (✔ ✘ ↷ —) · Diff : variante comparative requise si la fiche est sensible à la migration.

</details>
<!-- tracking:end -->

## Sommaire

1. [Gabarit commun](#1-gabarit-commun)
2. [`VehicleController`](#2-vehiclecontroller) — BH-001 à BH-056
3. [`ReservationController`](#3-reservationcontroller) — BH-100 à BH-179
4. [`ApiController`](#4-apicontroller) — BH-200 à BH-224
5. [`AdminController`](#5-admincontroller) — BH-300 à BH-337
6. [Commandes console](#6-commandes-console) — BH-400 à BH-460
7. [Journal d'implémentation](#journal-dimplémentation) · [Blocages](#blocages)

## 1. Gabarit commun

### 1.1 Ce qu'un test garantit

Un test de caractérisation affirme : **« pour cette entrée, sur ces données, l'application répond exactement
ainsi »**. Il est écrit et validé sur la référence (`app-ref`, step-00), puis rejoué inchangé à chaque marche (`app`).
Il ne juge pas : un bug figé est un comportement attendu, jusqu'à décision contraire consignée dans
`docs/migration/accepted-changes.md`.

Est **observable**, donc contractuel :

| Canal | Contractuel | Non contractuel (à ignorer ou normaliser) |
|---|---|---|
| Statut HTTP | toujours | — |
| En-têtes | `Location`, `Content-Type`, `X-Fleet-Version`, `Cache-Control` (valeur selon l'étape : ACC-001, § 1.2.6), `Allow`, `WWW-Authenticate`, présence et attributs de `Set-Cookie`, présence d'`Expires` (ACC-001) | `Date`, `Server`, `X-Powered-By`, `Content-Length`, valeur de `PHPSESSID`, valeur d'`Expires` |
| Corps HTML | contenu, ordre, libellés, liens, formats de nombres et de dates, échappement | indentation et espaces entre balises, jetons CSRF, pages d'erreur génériques du framework (voir § 1.2.4) |
| Corps JSON | octet par octet | — |
| Base de données | état exact après l'action (lecture seule = **inchangée**) | — |
| Journal PHP | signatures d'erreurs/notices produites (comparées à la référence, en suite `diff`) | horodatage, chemins absolus |

### 1.2 Gabarit d'un test

Chaque test (une méthode `bhNNN_…`) suit **exactement** ces étapes. Une étape non applicable est notée « N/A » dans
la fiche, jamais omise silencieusement.

#### 1.2.1 Fiche

```markdown
### BH-NNN — <titre : comportement observé, pas l'implémentation>
- Route / interface : <nom de route> · <MÉTHODE> <chemin + requête exacte>
- Suite : http | edge | diff | db          Groupe : zone-<zone>
- Scénario de données : <scénario>         (restauré avant le test, empreinte vérifiée)
- Authentification : aucune | Basic <utilisateur> | Basic invalide
- Sensible à la migration : non | oui → <quoi, quelle étape> (ex. « comparaison lâche, PHP 8.0, step-05 »)
- Entrée : <paramètres, en-têtes, corps>
- Attendu (step-00) :
  - A1 statut : …
  - A2 en-têtes : …
  - A3 corps : golden `<nom>` + assertions ciblées : …
  - A4 base : inchangée | <différence exacte>
  - A5 journal PHP : aucune entrée | <signatures attendues>
- Golden : tests/golden/http/<zone>/<nom>.html
```

#### 1.2.2 Déroulé (Arrange → Act → Assert)

1. **Arrange — données** : `Fixtures::restore('<scénario>', TARGET)` copie la base figée et vérifie son empreinte.
   Ne jamais dépendre de l'état laissé par un autre test.
2. **Arrange — contexte** : client HTTP **neuf** (pas de cookie, pas de redirection suivie automatiquement),
   authentification seulement si la fiche le dit. Horloge : celle du conteneur (`2026-01-15 10:00` au démarrage du
   processus) ; un test ne doit jamais dépendre des secondes.
3. **Arrange — référence** : noter la taille du journal PHP (`/var/log/php/error.log`) de la cible, pour A5.
4. **Act** : **une seule** requête, exactement celle de la fiche (méthode, chemin, chaîne de requête encodée telle
   quelle, en-têtes).
5. **Assert A1 — statut** : égalité stricte.
6. **Assert A2 — en-têtes contractuels** : égalité stricte des valeurs (insensible à la casse du nom).
   `Set-Cookie` : présence ou absence, attributs (`path=/`, `HttpOnly`), jamais la valeur.
7. **Assert A3 — corps** :
   - normaliser (`Normalizer::html()` : espaces entre balises, fins de ligne ; `Normalizer::json()` : rien) ;
   - comparer au **golden** (égalité stricte) ;
   - **et** faire 2 à 5 assertions ciblées sur ce que la fiche met en avant (nombre de lignes, ordre, libellé
     exact). Elles disent *pourquoi* le golden compte et localisent l'écart quand il casse.
8. **Assert A4 — base** : empreinte du dump canonique de la base = empreinte du scénario (route en lecture seule) ;
   pour une écriture, diff exact attendu (fiches des autres contrôleurs).
9. **Assert A5 — journal PHP** : nouvelles lignes depuis l'étape 3, réduites à leur signature (type + message sans
   chemin) ; égalité avec la liste de la fiche. Une notice PHP 7 qui devient warning PHP 8 est un **écart à
   trier**, pas un échec silencieux.
10. **Exécuter sur la référence** : le test doit être vert sur `app-ref` (`tests.sh --target ref`) avant d'être
    rendu. Rouge sur la référence = test ou fiche faux, jamais « à corriger dans l'application ».

#### 1.2.3 Variante différentielle (suite `diff`)

Pour chaque fiche marquée « sensible à la migration », un test `diff` rejoue la **même requête** sur `app-ref` et
`app` (même scénario restauré des deux côtés) et compare A1, A2, A3 normalisés et A5. Il ne connaît pas la valeur
attendue : il garantit seulement « identique à la référence », ce qui couvre aussi ce qu'aucune fiche n'a prévu.

#### 1.2.4 Règles de normalisation et décisions

| Élément | Règle |
|---|---|
| Espaces / indentation HTML | supprimés entre balises, conservés dans le texte |
| Pages d'erreur 404 / 405 génériques (« Oops! An Error Occurred » de Twig 2.8) | **corps non contractuel** par défaut (le gabarit d'erreur du framework change à chaque version) : on asserte statut + en-têtes. À confirmer par l'utilisateur et consigner dans `accepted-changes.md` |
| Corps des redirections 302 (HTML généré par `RedirectResponse`) | non contractuel ; `Location` l'est |
| `X-Powered-By` | non contractuel (dépend de la version de PHP) — mais signalé à `/vuln-audit` |
| Ordre des attributs HTML | conservé (une différence est un vrai écart) |

#### 1.2.5 Définition de « terminé » pour un test

- [ ] une fiche `BH-NNN` existe et le test porte son numéro (méthode `bhNNN_…`, `#[Group('BH-NNN')]`) ;
- [ ] scénario restauré dans le test, empreinte vérifiée ;
- [ ] A1 à A5 présents (ou « N/A » justifié dans la fiche) ;
- [ ] golden enregistré sur step-00 (`/golden-master --record`) ;
- [ ] vert sur `app-ref` ; aucun appel à une classe de l'application ;
- [ ] si « sensible à la migration » : variante `diff` et, le cas échéant, test `edge` dédié ;
- [ ] déterministe : 3 exécutions consécutives identiques, ordre des tests indifférent.

#### 1.2.6 Changements acceptés (`ACC-xxx`) : attentes par étape

Décisions de l'utilisateur consignées dans `docs/migration/accepted-changes.md`, reprises côté tests dans un **registre
unique** : `tests/src/Support/AcceptedChanges.php` (indexé par `ACC-xxx` et par étape d'application). Un ACC
s'applique aux attentes d'une cible **à partir de son étape** (`AcceptedChanges::stepAtLeast` / `applies`, étape servie
lue dans `APP_STEP` / `REF_STEP` du conteneur `tests`) : la référence `app-ref` (step-00) garde l'attente historique,
`app` (≥ step-01) reçoit l'attente acceptée. Aucune condition d'étape n'est écrite dans les tests eux-mêmes.

| ACC | Depuis | Fiches | Attente avant l'étape | Attente à partir de l'étape |
|---|---|---|---|---|
| ACC-001 | step-01 | BH-001 à BH-337, BH-460 (toute réponse HTTP ; suites http, edge, diff) | `Cache-Control: no-cache`, pas d'`Expires` | sans session : `Cache-Control: no-cache, private`, pas d'`Expires` ; avec session : `Cache-Control: max-age=0, must-revalidate, private` + `Expires` **présent** (valeur jamais comparée). Session = la réponse pose `PHPSESSID`, ou la requête envoyait le cookie (sauf 405 : rejet par le routage, avant la session — observé sur app step-01). Diff : valeur conforme ramenée à son équivalent step-00 des deux côtés, tout autre en-tête en égalité stricte |
| ACC-002 | step-01 | BH-425, BH-459 (suites cli, diff) | sortie complète de `list` / `help` comparée aux goldens `golden/cli/help/*.txt` | seule la partie propre aux commandes `fleet:*` : `list` → nom et description exacts ; `help` → synopsis, options propres (défaut de `--month` normalisé), texte d'aide ; code de retour et flux. En-tête de version, options globales et mise en page non vérifiés (goldens complets non consultés) |

Hors ACC mais lié (verdict TEST du triage step-01) : le message d'un bloc d'exception console (BH-415, BH-416, BH-418,
BH-419, BH-458) est extrait indépendamment de l'habillage du framework (`[Classe]` en 2.8, `In <fichier> line <n>:` en
3.4) — `Support\ConsoleRun::exceptionMessage`, valable à toutes les étapes.

## 2. `VehicleController`

Rappels communs à toutes les réponses de ce contrôleur (observés) :

- `X-Fleet-Version: 1.4.2`, `Cache-Control: no-cache`, `Content-Type: text/html; charset=UTF-8` — **y compris** sur
  les 302, 404 et 405 ; **ACC-001 à partir de step-01** : `Cache-Control: max-age=0, must-revalidate, private` +
  `Expires` sur les pages qui posent `PHPSESSID`, `no-cache, private` sans `Expires` sur les 302 / 404 / 405 (§ 1.2.6) ;
- pages HTML : `Set-Cookie: PHPSESSID=…; path=/; HttpOnly` (le layout lit les messages flash) ;
- titre `<title>Flotte Delvaux & Fils</title>`, `<nav>` avec le lien « Véhicules » ; lien « Ajouter un véhicule »
  **seulement** si authentifié `ROLE_ADMIN` ;
- lecture seule : **A4 = base inchangée** pour tous les tests de ce contrôleur.

### 2.1 `homepage` — `/` (toutes méthodes)

| Fiche | Requête | Scénario | A1 | A2 / A3 — points clés | Sensible |
|---|---|---|---|---|---|
| BH-001 | `GET /` | demo | 302 | `Location: /vehicules` (**relatif**) ; corps non contractuel | non |
| BH-002 | `POST /`, `PUT /`, `DELETE /` | demo | 302 | même `Location` : aucune restriction de méthode | oui : `@Method` absent, routage par attributs (step-04/06) |
| BH-003 | `GET /?status=1` | demo | 302 | `Location: /vehicules` : la chaîne de requête est **perdue** | non |
| BH-004 | `HEAD /` | demo | 302 | corps vide, mêmes en-têtes | non |

### 2.2 `vehicle_index` — `GET /vehicules`

| Fiche | Requête | Scénario | A1 | A3 — points clés | Sensible |
|---|---|---|---|---|---|
| BH-010 | `GET /vehicules` | demo | 200 | 5 lignes triées par plaque `1-ABC-123, 1-BCD-234, 1-CDE-345, 1-DEF-456, 2-EFG-567` ; colonnes Plaque (lien `/vehicules/{id}`), « Marque Modèle », `84.210 km`, libellé de statut ; option « Tous » sans `selected` | non |
| BH-011 | `GET /vehicules` | empty | 200 | une ligne `<td colspan="4">Aucun véhicule.</td>` | oui : `{% for … else %}` (Twig) |
| BH-012 | `GET /vehicules?status=0` / `=1` / `=2` | demo | 200 | 3 / 1 / 1 lignes ; option correspondante `selected` | non |
| BH-013 | `GET /vehicules?status=` | demo | 200 | 5 lignes, aucune option `selected` | non |
| BH-014 | `GET /vehicules?status=3` | demo | 200 | « Aucun véhicule. », aucune option `selected` | non |
| BH-015 | `GET /vehicules?status=abc` | demo | 200 | **3 lignes** (statut 0) **et option « Disponible » `selected`** (comparaison lâche PHP et Twig) | **oui** : PHP 8.0 → 0 ligne, aucune sélection (step-05) — test `edge` + `diff` |
| BH-016 | `GET /vehicules?status=0abc` | demo | 200 | 3 lignes | **oui** : PHP 8.0 (chaîne à préfixe numérique) |
| BH-017 | `GET /vehicules?status=01` et `?status=%201` | demo | 200 | 1 ligne (statut 1) | oui : chaînes numériques avec zéro / espace |
| BH-018 | `GET /vehicules?status%5B%5D=1` | demo | 200 | **0 ligne** (tableau ≠ entier) | **oui** : `InputBag::get()` sur un tableau lève une exception en Symfony 6 (→ 400 attendu) |
| BH-019 | `GET /vehicules` | edge | 200 | ordre **binaire** : ` 2-BBB-004` (espace en tête) en premier, puis `0-AAA-000`, `1ABC12`…, plaques en minuscules après les majuscules ; affichage **normalisé** (`2-BBB-004`, `ABC123`…) | oui : tri SQLite + filtre `plate` |
| BH-020 | `GET /vehicules` | edge | 200 | formats : `0 km`, `2.147.483.647 km` | oui : `number_format` |
| BH-021 | `GET /vehicules` | edge | 200 | échappement : `Citroën &lt;b&gt;C3&lt;/b&gt;`, `&quot;Aircross&quot; &amp; &lt;script&gt;…`, Unicode et emoji intacts | oui : auto-échappement Twig 1 → 3 |
| BH-022 | `GET /vehicules` | anomalies | 200 | statut inconnu → cellule de statut **vide** ; plaque vide, km négatif `-500 km` ; A5 : notices `Undefined offset: 3`, `Undefined offset: -1` ; plaque vide en **première** ligne, marque et modèle vides → cellule `<td> </td>` (une espace) ; A5 exact : `PHP Notice: Undefined offset: 3` puis `PHP Notice: Undefined offset: -1` (observé à l'implémentation) | **oui** : notice → warning (PHP 8), libellé |
| BH-023 | `GET /vehicules` (Basic `gestionnaire:flotte2016`) | demo | 200 | lien « Ajouter un véhicule » présent dans `<nav>` : `<a href="/admin/vehicules/nouveau">`, après « Véhicules » séparé par ` \| ` (observé à l'implémentation) | oui : sécurité (step-06) |
| BH-024 | `GET /vehicules` (Basic `gestionnaire:mauvais`) | demo | **401** | `WWW-Authenticate: Basic realm="Secured Area"` : de mauvais identifiants sur une page **publique** sont refusés | **oui** : authenticators (step-06) |
| BH-025 | `GET /vehicules/` | demo | 404 | barre oblique finale non redirigée | oui : routage |
| BH-026 | `POST /vehicules` | demo | 405 | `Allow: GET, HEAD` | non |
| BH-027 | `HEAD /vehicules` | demo | 200 | corps vide, mêmes en-têtes que GET | non |
| BH-028 | `GET /vehicules` | volume | 200 | 500 lignes ; suite `diff` : temps de réponse `app` ≤ 2 × `app-ref` (repère step-00 : ~40 ms) | oui : performance |

### 2.3 `vehicle_show` — `GET /vehicules/{id}`

| Fiche | Requête | Scénario | A1 | A3 — points clés | Sensible |
|---|---|---|---|---|---|
| BH-040 | `GET /vehicules/1` | demo | 200 | `<h1>1-ABC-123 — Renault Kangoo</h1>`, `Kilométrage : 84.210 km · Statut : Disponible`, lien `/vehicules/1/reserver` « Réserver ce véhicule », à venir : `15/06/2030 08:00 → 15/06/2030 18:00 : Sophie Martin` + lien `/reservations/5/retour` (libellé « retour » entre parenthèses, observé à l'implémentation), historique : `02/05/2016 : Jean Dupont, 210 km, 73,50 €` | oui : `{% for … if %}` (Twig 3, step-04) |
| BH-041 | `GET /vehicules/2` | demo | 200 | « Aucune réservation à venir. » ; historique de 2 lignes dans l'ordre des dates | oui : idem |
| BH-042 | `GET /vehicules/3` et `/4` | demo | 200 | statut « En entretien » / « Hors service », **pas** de lien « Réserver » | non |
| BH-043 | `GET /vehicules/<véhicule sans historique>` | edge (#1) | 200 | « Aucune réservation à venir. » et historique = `<ul>` **vide** (pas de message) | oui : Twig |
| BH-044 | `GET /vehicules/<véhicule en entretien avec réservation future>` | edge (#11) | 200 | réservation future affichée, pas de lien « Réserver » | non |
| BH-045 | `GET /vehicules/<véhicule en retard>` | edge (`1-OVD-001`) | 200 | la réservation **passée non rendue** figure dans « Réservations à venir », avant la future | non (bug figé) |
| BH-046 | `GET /vehicules/<même date de début>` | edge (`1-SAM-001`) | 200 | ordre des deux réservations de même début = ordre observé (dépend de la base) | oui : `@OrderBy`, ORM |
| BH-047 | `GET /vehicules/<40 réservations>` | edge (`1-LNG-040`) | 200 | toutes listées (aucune pagination), annulées absentes : 7 à venir, 29 dans l'historique (observé à l'implémentation) | non |
| BH-048 | `GET /vehicules/<arrondis>` | edge (`1-CST-001`) | 200 | historique : `1 km, 0,35 €` · `3 km, 1,05 €` · `7 km, 2,45 €` · `0 km, 0,00 €` · `999 km, 349,65 €` | oui : flottants, `number_format` |
| BH-049 | `GET /vehicules/<XSS>` | edge (`1-XSS-001`) | 200 | `<h1>` échappé (`&lt;script&gt;…&#039;x&#039;`), 11 conducteurs « difficiles » échappés (vide, 100 caractères, guillemets, script, emoji, arabe) | oui : échappement Twig |
| BH-050 | `GET /vehicules/<changements d'heure>` | edge (`1-DST-001`) | 200 | heures naïves affichées telles qu'en base (`29/03/2026 01:30 → 29/03/2026 03:30`) | oui : `DateTime`, fuseau |
| BH-051 | `GET /vehicules/<anomalies>` | anomalies (#1, #6, #8) | 200 | libellé de statut vide ; km retour < départ → `-100 km, -35,00 €` ; coût NULL → `0,00 €` ; coût `N/A` → `10 km,  €` (montant vide, warning) ; coût `12.345` → `12,35 €` ; statuts `CONFIRMEE`/vide absents des deux listes ; A5 : notice + warning `number_format()` ; statut inconnu (#1) : **pas** de lien « Réserver » (observé à l'implémentation) | **oui** : `number_format(null)` déprécié (8.1), `number_format('N/A')` → TypeError (8.0) → 500 probable |
| BH-052 | `GET /vehicules/01` | demo | **200** | même page que `/vehicules/1` (contenu dupliqué) | oui : ParamConverter → MapEntity (step-06) |
| BH-053 | `GET /vehicules/999`, `/vehicules/0` | demo | 404 | en-têtes communs ; corps non contractuel (§ 1.2.4) ; **pas** de `Set-Cookie` sur les 302/404/405 (observé à l'implémentation) | oui : page d'erreur |
| BH-054 | `GET /vehicules/abc`, `/vehicules/-1`, `/vehicules/99999999999999999999` | demo | 404 | prérequis `\d+` / dépassement d'entier : 404, jamais 500 | **oui** : très grand id (PHP 8 / DBAL 3) |
| BH-055 | `GET /vehicules/1/` | demo | 404 | barre oblique finale | oui : routage |
| BH-056 | `POST /vehicules/1` | demo | 405 | `Allow: GET, HEAD` | non |

### 2.4 Exemple complet : BH-015

```markdown
### BH-015 — Le filtre de statut non numérique affiche les véhicules disponibles
- Route / interface : vehicle_index · GET /vehicules?status=abc
- Suite : http + edge (+ diff)     Groupe : zone-vehicles-list
- Scénario de données : demo
- Authentification : aucune
- Sensible à la migration : oui → comparaison lâche `0 != 'abc'` (PHP) et `status == value` (Twig) ; PHP 8.0
  (step-05) donnera 0 ligne et aucune option sélectionnée
- Entrée : paramètre de requête status=abc
- Attendu (step-00) :
  - A1 statut : 200
  - A2 en-têtes : Content-Type text/html; charset=UTF-8 · X-Fleet-Version 1.4.2 · Cache-Control no-cache ·
    Set-Cookie PHPSESSID (path=/, HttpOnly) — ACC-001 à partir de step-01 (session ouverte) : Cache-Control
    max-age=0, must-revalidate, private · Expires présent (valeur non comparée)
  - A3 corps : golden `vehicles-list/bh015-status-abc` ; 3 lignes : 1-ABC-123, 1-BCD-234, 2-EFG-567 ;
    `<option value="0" selected>Disponible</option>` ; aucune autre option sélectionnée
  - A4 base : inchangée
  - A5 journal PHP : aucune entrée (comparé en suite diff uniquement, proposition § 2.5)
- Golden : tests/golden/http/vehicles-list/bh015-status-abc.html
- Tests : VehiclesListTest::bh015_status_filter_with_non_numeric_value (http),
  LooseStatusFilterTest::bh015_non_numeric_status_matches_available_vehicles (edge),
  VehiclesListDiffTest::bh015_non_numeric_status_same_as_reference (diff)
```

Déroulé correspondant (pseudo-code, pas une implémentation) :

```text
restore('demo', target)                     → empreinte OK
log0 = phpLogSize(target)
r = http(target).get('/vehicules?status=abc')   (client neuf, sans redirection)
assert r.status == 200
assert headers(r) ⊇ {Content-Type, X-Fleet-Version: 1.4.2}; cookie PHPSESSID présent
assert Cache-Control == AcceptedChanges.expected(r)   (step-00 : no-cache ; ≥ step-01 : max-age=0, must-revalidate, private)
assert has(Expires) == AcceptedChanges.expectsExpires(r)   (step-00 : absent ; ≥ step-01 : présent, valeur ignorée)
html = normalizeHtml(r.body)
assertMatchesGolden('http/vehicles-list/bh015-status-abc.html', html)
assert rows(html) == ['1-ABC-123', '1-BCD-234', '2-EFG-567']
assert selectedOptions(html) == ['0']
assert dbFingerprint(target) == fingerprint('demo')
(A5 : phpLogSignatures(ref) == phpLogSignatures(app), en suite diff uniquement)
```

### 2.5 Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| Corps des pages d'erreur 404/405 du framework : contractuels ? | **Non** (statut et en-têtes seulement) — à consigner dans `accepted-changes.md` |
| BH-015 à BH-018 (filtre lâche, tableau) : conserver le comportement PHP 7 en 8.4 ? | **Oui** pendant la migration (bug pour bug) ; correction éventuelle dans une étape séparée |
| BH-024 (401 sur page publique avec mauvais identifiants) : à préserver ? | Oui, c'est un comportement visible des clients HTTP |
| BH-052 (`/vehicules/01` → 200) : à préserver ? | Oui (liens externes possibles) |
| A5 (journal PHP) : contractuel ou seulement comparé en `diff` ? | Comparé en `diff` uniquement : une notice PHP 7 devient warning en PHP 8 sans changer la page |
| Seuil de performance BH-028 | ≤ 2 × la référence |

## 3. `ReservationController` — création de réservation et retour de véhicule

> **Objet** : appliquer le gabarit commun (§ 1 : fiche `BH-NNN`, déroulé
> Arrange → Act → Assert, A1 à A5, variante `diff`, définition de « terminé ») aux 2 routes **qui écrivent en base** :
> création d'une réservation et retour d'un véhicule.
>
> Toutes les valeurs « observées » viennent de l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), par des
> soumissions de formulaires complètes (session, jeton CSRF) sur les scénarios `booking`, `returns` et `anomalies`,
> avec comparaison de la base avant / après (2026-11-17).

### 3.1 Ce qui change par rapport au gabarit commun

Ces routes **modifient la base** et passent par un formulaire protégé par CSRF. Le déroulé d'un test devient un
**parcours** de 3 requêtes au lieu d'une :

1. **Arrange** : restaurer le scénario, client neuf **avec session** (cookies conservés), noter la taille du journal PHP.
2. **GET du formulaire** (`/vehicules/{id}/reserver` ou `/reservations/{id}/retour`) : extraire le jeton
   `reservation[_token]` / `form[_token]` (cas « sans jeton » ou « jeton faux » : ne pas l'envoyer / en envoyer un faux).
3. **Act — POST** : `application/x-www-form-urlencoded`, champs **exactement** ceux de la fiche (valeurs brutes,
   pas de reformatage par le test).
4. **Assert A1/A2** sur la réponse au POST : `302` + `Location` relatif, ou `200` (formulaire réaffiché), ou erreur.
5. **Assert A3** :
   - 302 → **GET de `Location`** avec la même session et vérification du message flash
     `<p class="flash-success">…</p>` (il n'apparaît qu'une fois : un second GET ne l'affiche plus) ;
   - 200 → erreurs du formulaire, **texte exact et emplacement** : erreur globale (règle métier, CSRF, champs en trop)
     en tête du formulaire, erreur de champ (`This value is not valid.`) dans le `<div>` du champ, avant l'`<input>` ;
     valeurs saisies réaffichées (`value="…"`) ;
   - golden du HTML normalisé (jeton CSRF masqué).
6. **Assert A4 — diff exact de la base** (le point le plus important pour ces routes) : lignes ajoutées et modifiées,
   colonne par colonne, **types SQLite compris** (`quote()` : un coût `35` INTEGER n'est pas `35.0` REAL), et **rien
   d'autre**. Cas refusés : base **strictement inchangée**.
7. **Assert A5** : journal PHP (attention : en step-00 les exceptions ne sont **journalisées nulle part**, MonologBundle
   n'est pas installé — le 500 ne se constate que par la réponse et la base).

Rappels observés communs aux deux routes :

- les formulaires n'ont **aucun bouton de soumission** (`{{ form(form) }}` sans bouton) : seule la touche Entrée soumet ;
- messages Symfony **en anglais** (pas de traducteur configuré), messages métier en français ;
- `GET` du formulaire : 200 même quand la soumission sera refusée (véhicule indisponible, réservation clôturée) ;
- `POST` sans aucun champ du formulaire : **200 sans erreur**, rien n'est écrit (`isValid()` appelé sur un formulaire
  non soumis — **lève une exception à partir de Symfony 4**) ;
- en-têtes : `X-Fleet-Version: 1.4.2`, `Cache-Control: no-cache`, cookie `PHPSESSID` ; 404 si l'id n'existe pas,
  405 pour une autre méthode que GET/POST. **ACC-001 à partir de step-01** : réponses du parcours (session) en
  `Cache-Control: max-age=0, must-revalidate, private` + `Expires` ; 405 en `no-cache, private` (§ 1.2.6).

### 3.2 `reservation_new` — `GET|POST /vehicules/{id}/reserver`

Formulaire `reservation` : `driverName` (texte), `startAt` et `endAt` (texte `dd/MM/yyyy HH:mm`), `_token`.
Page : `<h1>Réserver 1-BOK-001</h1>`, `<p>Format des dates : JJ/MM/AAAA HH:MM</p>`.
Création réussie → ligne `reservation` : `status = 'confirmee'`, `start_km` = kilométrage du véhicule,
`end_km` et `cost` NULL ; 302 vers `/vehicules/{id}` ; flash « Réservation enregistrée. ».

Scénario `booking` (date de référence 15/01/2026 10:00) : véhicule 2 réservé le 10/02/2026 10:00–12:00 ; 3 en
entretien ; 4 hors service ; 5 n'a sur le créneau qu'une réservation terminée et une annulée ; 6 en cours (14/01 → 16/01) ;
7 en retard (05/01 → 06/01, jamais rendu) ; 8 à 0 km.

| Fiche | Entrée (POST sauf mention) | Scénario | A1 | A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-100 | `GET /vehicules/1/reserver` | booking | 200 | titre, rappel du format, 3 champs `required`, jeton caché, **pas de bouton** | oui : rendu des formulaires (Twig/Form, step-01→04) |
| BH-101 | véhicule 1, `Jean Test`, `20/02/2026 08:00` → `20/02/2026 18:00` | booking | 302 | `Location: /vehicules/1` ; flash « Réservation enregistrée. » ; A4 : +1 ligne id 11 `(1, 'Jean Test', '2026-02-20 08:00:00', '2026-02-20 18:00:00', 'confirmee', 10000, NULL, NULL)` | oui : `isValid()` sans `isSubmitted()`, types par nom (step-01/02) |
| BH-102 | véhicule 3 (entretien) puis 4 (hors service), créneau libre | booking | 200 | erreur globale « Ce véhicule n'est pas disponible. » ; base inchangée | non |
| BH-103 | fin = début (`08:00` → `08:00`) | booking | 200 | « La date de fin doit être postérieure à la date de début. » | non |
| BH-104 | fin avant début | booking | 200 | même message | non |
| BH-105 | véhicule 2, `10/02/2026 10:30` → `11:30` (inclus) | booking | 200 | « Ce véhicule est déjà réservé sur cette période. » | non |
| BH-106 | véhicule 2, `08:00` → `10:00` (se termine pile au début de l'existante) | booking | 200 | **refusé** : bornes incluses, se toucher = chevaucher | oui : requête DQL (ORM 2.5 → 2.17/3) |
| BH-107 | véhicule 2, `12:00` → `14:00` (commence pile à la fin) | booking | 200 | **refusé** (bornes incluses) | oui : idem |
| BH-108 | véhicule 2, `09:00` → `13:00` (englobe) | booking | 200 | refusé | non |
| BH-109 | véhicule 2, `08:00` → `09:59` puis `12:01` → `14:00` | booking | 302 | acceptés (une minute d'écart suffit) ; +1 ligne chacun | non |
| BH-110 | véhicule 5, créneau d'une réservation **terminée** et d'une **annulée** | booking | 302 | accepté : seules les `confirmee` bloquent | non |
| BH-111 | véhicule 2, `20/02/2026 09:00` → `10:00` (sur une annulée) | booking | 302 | accepté | non |
| BH-112 | véhicule 6, `15/01/2026 12:00` → `14:00` (pendant une réservation en cours) | booking | 200 | « déjà réservé » | non |
| BH-113 | véhicule 7, `05/01/2026 12:00` → `14:00` (créneau d'une réservation **en retard**) | booking | 200 | « déjà réservé » : une réservation passée non rendue bloque toujours son créneau | non |
| BH-114 | véhicule 1, `01/12/2025 08:00` → `10:00` (dans le **passé**) | booking | 302 | **accepté** : aucun contrôle de date passée | non |
| BH-115 | véhicule 8 (0 km) | booking | 302 | `start_km = 0` | non |
| BH-116 | conducteur vide `""`, puis `"   "` | booking | 200 | « Le nom du conducteur est obligatoire. » (les espaces sont supprimés avant validation) ; **erreur de champ** dans le `<div>` de `driverName` (avant l'`<input>`), aucune erreur globale (observé à l'implémentation) | oui : option `trim`, NotBlank |
| BH-117 | conducteur `"  Paul Martin  "` | booking | 302 | enregistré **`Paul Martin`** (espaces supprimés) | oui : idem |
| BH-118 | conducteur de **101** caractères | booking | 302 | **accepté et enregistré en entier** (colonne VARCHAR(100) non contrôlée par SQLite, aucune contrainte `Length`) ; idem avec 101 caractères multi-octets (`é` ×101 → `length() = 101`) (observé à l'implémentation) | **oui** : DBAL 3 / validation |
| BH-119 | conducteur `<script>alert(1)</script> Émile 🚐` | booking | 302 | enregistré tel quel ; affiché **échappé** sur la fiche véhicule (lien avec BH-049) | oui : échappement Twig |
| BH-120 | dates `2026-02-20 08:00` (ISO) | booking | 200 | `This value is not valid.` sur **les deux** champs (erreur de champ, en anglais) | oui : `IntlDateFormatter`, messages |
| BH-121 | dates `31/02/2026 08:00` ; `20/02/2026 25:00` ; `20/02/2026 8` (sans minutes) | booking | 200 | `This value is not valid.` ×2 (dates non « lenient ») | oui : ICU |
| BH-122 | dates `20/02/26 08:00` → `20/02/26 18:00` (**année sur 2 chiffres**) | booking | 302 | **accepté**, enregistré **`0026-02-18 07:42:30`** → `0026-02-18 17:42:30` : an 26, **décalé de 2 jours** (calendrier julien) et de **−17 min 30 s** (heure solaire de Bruxelles avant 1892) | **oui, très** : ICU, `DateTime`, PHP 8 (step-05), DBAL 3 |
| BH-123 | dates `1/2/2026 8:05` → `1/2/2026 9:05` (chiffres uniques) | booking | 302 | accepté : `2026-02-01 08:05:00` | oui : ICU |
| BH-124 | dates avec secondes `20/02/2026 08:00:30` | booking | 302 | accepté, **secondes supprimées** (`08:00:00`) | oui : ICU |
| BH-125 | `29/03/2026 02:30` → `03:30` (heure **inexistante**, passage à l'heure d'été) | booking | 200 | `This value is not valid.` sur `startAt` uniquement | **oui** : ICU / fuseau |
| BH-126 | `25/10/2026 02:30` → `02:45` (heure **ambiguë**, passage à l'heure d'hiver) | booking | 302 | accepté, enregistré tel quel | oui : idem |
| BH-127 | dates en `31/12/9999` | booking | 200 | `This value is not valid.` ×2 | oui : ICU, dépassement |
| BH-128 | minutes quelconques `08:07` → `08:08` (1 minute) | booking | 302 | accepté (pas de pas imposé) | non |
| BH-129 | sans jeton CSRF, puis jeton faux | booking | 200 | erreur globale `The CSRF token is invalid. Please try to resubmit the form.` ; base inchangée | oui : composant CSRF (step-04/06) |
| BH-130 | champ en trop dans le formulaire `reservation[status]=terminee` | booking | 200 | `This form should not contain extra fields.` ; **pas** d'affectation | oui : `allow_extra_fields` |
| BH-131 | champ en trop **hors** du formulaire (`foo=bar`) | booking | 302 | ignoré, réservation créée | non |
| BH-132 | seulement le jeton (aucun champ) | booking | 200 | 3 erreurs : « Le nom du conducteur est obligatoire. », `This value should not be blank.` ×2 (mélange FR / EN) — trois **erreurs de champ**, une par champ (`driverName`, `startAt`, `endAt`), aucune erreur globale (observé à l'implémentation) | oui : messages de validation |
| BH-133 | POST **sans corps** | booking | 200 | formulaire réaffiché **sans erreur**, rien écrit | **oui** : Symfony 4 → exception (500) attendue sans précaution |
| BH-134 | même POST envoyé **deux fois** (même jeton) | booking | 302 puis 200 | 1re : créée ; 2e : « déjà réservé » ; **1 seule** ligne | non |
| BH-135 | `GET /vehicules/3/reserver` (véhicule en entretien) | booking | 200 | formulaire affiché normalement (refus seulement à la soumission) | non |
| BH-136 | `POST /vehicules/999/reserver` ; `GET /vehicules/abc/reserver` | booking | 404 | base inchangée | oui : ParamConverter → MapEntity |
| BH-137 | `PUT /vehicules/1/reserver` | booking | 405 | `Allow: GET, POST, HEAD` | non |
| BH-138 | réservation créée puis **fiche véhicule** | booking | 302 → 200 | la nouvelle réservation apparaît dans « Réservations à venir » avec son lien de retour (`/reservations/11/retour`) : `20/02/2026 08:00 → 20/02/2026 18:00 : Jean Test (retour)` ; historique vide (observé à l'implémentation) | oui : lien avec BH-040 |

### 3.3 `reservation_return` — `GET|POST /reservations/{id}/retour`

Formulaire `form` : `endKm` (`<input type="number">`), `_token`. Page : `<h1>Retour de 1-RET-001</h1>`,
`<p>Kilométrage au départ : 10.000 km</p>`.
Retour réussi → `status = 'terminee'`, `end_km`, `cost = round(km × 0,35, 2)`, **kilométrage du véhicule =
`end_km`** ; 302 vers `/vehicules/{id}` ; flash « Retour enregistré. Coût : 35,00 € ».

Scénario `returns` : réservation 1 (véhicule 1, départ 10 000 km), 2 (départ 0), 3 terminée, 4 annulée, 5 (départ
50 000, véhicule déjà à 50 500), 6 (départ 2 147 483 000), 7 (véhicule en entretien), 8 future et 9 en cours sur le
même véhicule 8 (départ 8 000).

| Fiche | Entrée (POST `form[endKm]` sauf mention) | Scénario | A1 | A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-150 | `GET /reservations/1/retour` | returns | 200 | titre, kilométrage au départ formaté, champ `type="number"` `required`, **pas de bouton** | oui : rendu des formulaires |
| BH-151 | réservation 1, `10100` | returns | 302 | `Location: /vehicules/1` ; flash « Retour enregistré. Coût : 35,00 € » ; A4 : r1 `status 'terminee'`, `end_km 10100`, `cost 35` (**INTEGER**) ; v1 `mileage 10100` | oui : arithmétique, types (step-05, DBAL 3) |
| BH-152 | réservation 1, `10000` (0 km) | returns | 302 | « Coût : 0,00 € » ; `cost 0` ; kilométrage du véhicule inchangé | non |
| BH-153 | réservation 1, `9999` (moins qu'au départ) | returns | 200 | « Kilométrage invalide. » ; base inchangée ; **erreur globale** en tête du formulaire, saisie réaffichée `value="9999"` (observé à l'implémentation ; même emplacement pour BH-154, 155, 165, 168, 170) | non |
| BH-154 | réservation 3 (**terminée**) | returns | 200 | « Cette réservation est déjà clôturée. » | non |
| BH-155 | réservation 4 (**annulée**) | returns | 200 | **même** message « déjà clôturée » | non |
| BH-156 | réservation 2, `1` / `3` / `7` / `13` km | returns | 302 | coûts `0,35 €` / `1,05 €` / `2,45 €` / `4,55 €` ; stockés `0.35`, `1.05`, `2.45`, `4.55` (REAL) | **oui** : flottants, `round`, `number_format` |
| BH-157 | réservation 7 (véhicule **en entretien**), `7050` | returns | 302 | accepté, `cost 17.5` (REAL), « 17,50 € » | non |
| BH-158 | réservation 5 (`50200` alors que le véhicule est à 50 500) | returns | 302 | accepté ; **le kilométrage du véhicule recule** à 50 200 ; « 70,00 € » ; `cost 70` (INTEGER) (observé à l'implémentation) | non |
| BH-159 | réservation 8 (**pas encore commencée**), `8100` | returns | 302 | accepté (aucun contrôle de date) | non |
| BH-160 | réservation 9 (en cours, même véhicule que 8), `8300` | returns | 302 | r9 clôturée « 105,00 € » (`cost 105` INTEGER, observé à l'implémentation), v8 à 8 300 ; **r8 reste `confirmee` avec `start_km 8000`** | non |
| BH-161 | réservation 6, `2147484000` | returns | 302 | « 350,00 € » ; **kilométrage du véhicule > 2^31-1** enregistré ; `end_km 2147484000` et `cost 350` de type INTEGER (observé à l'implémentation) | oui : entiers 32/64 bits, DBAL |
| BH-162 | `99999999999999999999` | returns | 200 | `This value is not valid.` (champ) | oui : `IntegerType`, ICU |
| BH-163 | `abc` ; `+10100` ; `10 100` (espace) | returns | 200 | `This value is not valid.` | oui : `IntegerType` (locale `fr`) |
| BH-164 | `10100.6` et `10100,6` | returns | 302 | **tronqué à 10100** (arrondi vers le bas), 35,00 € | **oui** : `IntegerType` (`rounding_mode`, `scale`), step-02/04 |
| BH-165 | `10.100` (point des milliers) | returns | 200 | interprété comme **10** → « Kilométrage invalide. » | **oui** : idem, locale |
| BH-166 | `010100` (zéro initial) | returns | 302 | accepté = 10100 | oui : idem |
| BH-167 | `1e4` | returns | 302 | **interprété comme 10000** → clôture à 0 km, « 0,00 € » ; `end_km 10000`, `cost 0` (INTEGER), kilométrage du véhicule inchangé (observé à l'implémentation) | **oui** : idem |
| BH-168 | réservation 1, valeur **vide** | returns | 200 | « Kilométrage invalide. » (`null < 10000`) | **oui** : comparaison `null` (PHP 8) |
| BH-169 | réservation 2 (départ **0**), valeur **vide** | returns | **500** | page d'erreur générique ; **base inchangée** ; aucune trace dans un journal (cause déduite : kilométrage du véhicule mis à `NULL` → violation `NOT NULL`, transaction annulée) | **oui** : erreur différente attendue en PHP 8 / DBAL 3 |
| BH-170 | réservation 2, `-5` | returns | 200 | « Kilométrage invalide. » | non |
| BH-171 | sans jeton CSRF, puis jeton faux | returns | 200 | `The CSRF token is invalid. Please try to resubmit the form.` ; base inchangée | oui : CSRF |
| BH-172 | champ en trop `form[cost]=0` | returns | 200 | `This form should not contain extra fields.` | oui : `allow_extra_fields` |
| BH-173 | POST **sans corps** | returns | 200 | formulaire réaffiché sans erreur, rien écrit | **oui** : Symfony 4 (`isValid()` non soumis) |
| BH-174 | même POST **deux fois** | returns | 302 puis 200 | 1re : clôturée ; 2e : « déjà clôturée » ; une seule mise à jour | non |
| BH-175 | `GET /reservations/3/retour` (terminée) | returns | 200 | formulaire affiché normalement | non |
| BH-176 | `POST /reservations/999/retour` ; `GET /reservations/abc/retour` | returns | 404 | base inchangée | oui : ParamConverter |
| BH-177 | `PUT /reservations/1/retour` | returns | 405 | `Allow: GET, POST, HEAD` | non |
| BH-178 | réservation `terminee` du scénario `anomalies` (id 3, `end_km` NULL) | anomalies | 200 | « déjà clôturée » | non |
| BH-179 | retour réussi puis **fiche véhicule** | returns | 302 → 200 | la réservation passe de « Réservations à venir » à « Historique » (`10100 − 10000 = 100 km, 35,00 €`) ; kilométrage affiché `10.100 km` ; « Aucune réservation à venir. » ; historique exact : `02/01/2026 : Chloé Dubois, 100 km, 35,00 €` puis `14/01/2026 : Jean Dupont, 100 km, 35,00 €` (date de **début**) (observé à l'implémentation) | oui : lien avec BH-040 |

### 3.4 Exemple complet : BH-122

```markdown
### BH-122 — Une année saisie sur 2 chiffres est enregistrée en l'an 26, décalée
- Route / interface : reservation_new · POST /vehicules/1/reserver
- Suite : http + edge (+ diff)            Groupe : zone-reservation-new
- Scénario de données : booking
- Authentification : aucune
- Sensible à la migration : oui, très → le format ICU `dd/MM/yyyy` accepte `26` comme année 26 ; la conversion en
  DateTime applique le calendrier julien (−2 jours) et l'heure solaire de Bruxelles d'avant 1892 (−17 min 30 s).
  Toute montée d'ICU, de PHP (step-03/05/07) ou de DBAL (step-06) peut changer ce résultat.
- Entrée : reservation[driverName]=X, reservation[startAt]=20/02/26 08:00, reservation[endAt]=20/02/26 18:00, jeton valide
- Attendu (step-00) :
  - A1 statut : 302
  - A2 en-têtes : Location /vehicules/1 · X-Fleet-Version 1.4.2
  - A3 corps : (après GET de Location) flash « Réservation enregistrée. » ; « Réservations à venir » = exactement
    `18/02/0026 07:42 → 18/02/0026 17:42 : X (retour)` (lien `/reservations/11/retour`)
  - A4 base : diff exact = `+ reservation: 11|1|'X'|'0026-02-18 07:42:30'|'0026-02-18 17:42:30'|'confirmee'|10000|NULL|NULL`
    (dates de type TEXT) ; rien d'autre
  - A5 journal PHP : aucune entrée (comparé en suite diff)
- Golden : tests/golden/http/reservation-new/bh122-two-digit-year.html
- Tests : ReservationNewTest::bh122_two_digit_year_is_stored_as_year_26_shifted (http),
  ReservationInputParsingTest::bh122_two_digit_year_becomes_julian_year_26_with_brussels_solar_time (edge),
  ReservationNewDiffTest::bh122_two_digit_year_same_as_reference (diff)
```

Note : les secondes `:30` de A4 ne viennent pas de l'horloge mais du décalage déterministe de l'heure solaire
(−17 min 30 s) : elles sont assertées.

### 3.5 Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| BH-122 (année sur 2 chiffres → an 26 décalé), BH-164/165/167 (`10100,6` tronqué, `10.100` = 10, `1e4` = 10000) : préserver ? | **Oui** pendant la migration (données réellement enregistrées) ; corrections dans une étape séparée, après décision |
| BH-169 (500 sur retour vide quand le départ est à 0) : figer le 500 ? | Figer **statut 500 + base inchangée** ; corps non contractuel (page d'erreur du framework) |
| BH-133 / BH-173 (POST vide → 200 sans erreur) : préserver malgré Symfony 4 ? | Oui : le migrator devra ajouter le contrôle `isSubmitted()` **sans** changer la réponse |
| Messages Symfony en anglais (`This value is not valid.`…) : contractuels ? | Oui (texte visible par l'utilisateur) — toute traduction future est un changement accepté à consigner |
| Absence de bouton de soumission : préserver ? | Oui (le HTML est contractuel) ; à signaler comme bug d'ergonomie |
| Exceptions non journalisées (pas de MonologBundle) | Hors contrat de test ; à signaler pour la migration (ajout de journalisation = changement accepté) |

> Toutes les propositions ci-dessus sont appliquées **à titre provisoire** par les tests (2026-11-17), en attente de
> décision de l'utilisateur ; aucune ne bloque une fiche. Voir le journal d'implémentation.

## 4. `ApiController` — API JSON du planning des dépôts

> **Objet** : appliquer le gabarit commun (§ 1 : fiche `BH-NNN`, déroulé
> Arrange → Act → Assert, A1 à A5, variante `diff`, définition de « terminé ») à l'unique route de l'API JSON,
> consommée par **un client externe** (« l'écran de planning des dépôts ») : c'est la route où un écart, même d'un
> octet, casse quelqu'un d'autre.
>
> Valeurs observées sur l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), scénarios `empty`, `demo`,
> `booking`, `edge`, `anomalies`, `nominal`, `volume`, le 2026-11-17.

### 4.1 Ce qui change par rapport au gabarit commun

- **A3 = comparaison octet par octet** du corps (aucune normalisation) contre le golden : l'ordre des clés,
  l'échappement des caractères, les types JSON (`84210` ≠ `"84210"`, `null` ≠ `""`) et l'absence de retour à la ligne
  final sont contractuels. Une empreinte SHA-256 du corps est stockée avec le golden pour un diagnostic rapide.
- En complément, **assertions structurelles** (après `json_decode`) pour localiser l'écart : nombre d'éléments, ordre
  des plaques, clés exactes `id, plate, label, mileage, status, nextReservation` dans cet ordre, types.
- **Lecture seule** : A4 = base inchangée pour toutes les fiches.
- Les **erreurs** (404, 405, 401) ne sont **pas** du JSON mais les pages HTML génériques du framework : on asserte
  statut et en-têtes, corps non contractuel (§ 1.2.4).

Format observé de la réponse (scénario `demo`, 591 octets, empreinte `6fe447542af8…`) :

```json
[{"id":1,"plate":"1-ABC-123","label":"Renault Kangoo","mileage":84210,"status":"Disponible","nextReservation":"15\/06\/2030 08:00"},{"id":2,"plate":"1-BCD-234","label":"Peugeot Partner","mileage":120450,"status":"Disponible","nextReservation":""},…]
```

Règles d'encodage observées (`JsonResponse` de Symfony 2.8 : `JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP |
JSON_HEX_QUOT`, sans `JSON_UNESCAPED_UNICODE` ni `JSON_UNESCAPED_SLASHES`) :

| Caractère | Encodé | Exemple observé |
|---|---|---|
| `/` | `\/` | `"15\/06\/2030 08:00"` |
| non ASCII | &#92;uXXXX en hexadécimal **minuscule** (paires de substitution pour les emoji) | Citro&#92;u00ebn ; 🚐 → &#92;ud83d&#92;ude90 (observé à l'implémentation) |
| `<` `>` | &#92;u003C &#92;u003E (hexadécimal **majuscule**) | &#92;u003Cscript&#92;u003E (observé à l'implémentation) |
| `&` `'` `"` | &#92;u0026 &#92;u0027 &#92;u0022 (majuscule) | &#92;u0022Aircross&#92;u0022 &#92;u0026 (observé à l'implémentation) |

> Note d'outillage (2026-11-17) : dans ce document et dans les sources de tests, ne jamais écrire la séquence
> « barre oblique inverse + u + 4 chiffres hexadécimaux » telle quelle : l'outil d'écriture de fichiers de Claude Code la
> décode silencieusement (c'est ce qui avait vidé ce tableau). Documents : entité `&#92;` ; tests :
> `Support\ApiJson::lit('Citro{u00eb}n')`.

### 4.2 `api_vehicles` — `GET /api/vehicules`

Champs par véhicule, dans cet ordre : `id` (entier), `plate` (plaque **brute** de la base, non normalisée),
`label` (`"<marque> <modèle>"`), `mileage` (entier), `status` (libellé ou `null`), `nextReservation` (début de la
**première réservation `confirmee` dans l'ordre des dates**, même passée, au format `d\/m\/Y H:i`, ou `""`).
Tri : plaque, ordre binaire SQLite.

| Fiche | Requête | Scénario | A1 | A2 / A3 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-200 | `GET /api/vehicules` | empty | 200 | corps exactement `[]` (2 octets) | oui : `JsonResponse` (encodage d'un tableau vide) |
| BH-201 | `GET /api/vehicules` | demo | 200 | 591 octets, golden + empreinte ; 5 éléments ; `nextReservation` `"15\/06\/2030 08:00"` pour 1-ABC-123, `""` sinon | **oui** : options d'encodage JSON (Symfony 2.8 → 6.4), sérialisation |
| BH-202 | en-têtes de la réponse | demo | 200 | `Content-Type: application/json` (**sans** `charset`) ; `X-Fleet-Version: 1.4.2` ; `Cache-Control: no-cache` (**ACC-001 à partir de step-01** : `no-cache, private`, sans `Expires`) ; **aucun** `Set-Cookie`, aucun en-tête CORS, `ETag` ni `Last-Modified` | **oui** : `JsonResponse`, `Content-Type`, session |
| BH-203 | octets du corps | demo | 200 | pas de BOM, pas de retour à la ligne final (se termine par `"}]`), aucun espace hors des valeurs | oui : encodage |
| BH-204 | types JSON | demo | 200 | `id` et `mileage` **nombres**, les autres **chaînes** | oui : DBAL 3 (entiers rendus en chaînes ?) |
| BH-205 | `GET /api/vehicules` | edge | 200 | ordre binaire des plaques **brutes** : `" 2-BBB-004 "` (espaces conservés) en premier, puis `0-AAA-000`, `1-ABC-12`… ; `1-aaa-003` en minuscules (non normalisée, contrairement au HTML) ; 27 éléments, 3 458 octets, `1-aaa-003` entre `1-XSS-001` et `2.BBB.006`, `Z-ZZZ-999` en dernier (observé à l'implémentation) | oui : tri SQLite, plaque brute |
| BH-206 | caractères spéciaux | edge | 200 | `label` du véhicule 18 = "Citro&#92;u00ebn &#92;u003Cb&#92;u003EC3&#92;u003C\/b&#92;u003E &#92;u0022Aircross&#92;u0022 &#92;u0026 &#92;u003Cscript&#92;u003Ealert(&#92;u0027x&#92;u0027)&#92;u003C\/script&#92;u003E" ; véhicule 20 : "&#92;u0160koda &#92;u00c9nergie &#92;u00d6l&#92;u00e7&#92;u00fc &#92;u00d1and&#92;u00fa &#92;u96fb&#92;u6c17 &#92;ud83d&#92;ude90" ; corps 100 % ASCII (observé à l'implémentation) | **oui, très** : drapeaux `JSON_HEX_*`, `JSON_UNESCAPED_*` |
| BH-207 | grands nombres | edge | 200 | `"mileage":2147483647` (nombre, pas de notation exponentielle) | oui : entiers, DBAL |
| BH-208 | longueur maximale | edge | 200 | `label` de 101 caractères (50 + espace + 50) | non |
| BH-209 | réservation **en retard** | edge (1-OVD-001) | 200 | `"nextReservation":"05\/01\/2026 08:00"` : la réservation passée non rendue est présentée comme la « prochaine » | non (bug figé) |
| BH-210 | même date de début | edge (1-SAM-001) | 200 | `"20\/02\/2026 08:00"` | oui : ordre des réservations (`@OrderBy`, ORM) |
| BH-211 | statuts du véhicule | edge (11, 12) | 200 | « En entretien » avec `"03\/02\/2026 08:00"` ; « Hors service » avec la réservation en cours `"14\/01\/2026 08:00"` | non |
| BH-212 | longue liste de réservations | edge (1-LNG-040) | 200 | `nextReservation` = première confirmée (`"19\/01\/2026 08:00"`), annulées et terminées ignorées | non |
| BH-213 | heure d'été | edge (1-DST-001) | 200 | `"29\/03\/2026 01:30"` (heure naïve, telle qu'en base) | oui : `DateTime`, fuseau |
| BH-214 | création et blocages | booking | 200 | 8 éléments ; `nextReservation` : `"10\/02\/2026 10:00"` (BOK-002), `"02\/02\/2026 08:00"` (BOK-003 en entretien), `"14\/01\/2026 08:00"` (en cours), `"05\/01\/2026 08:00"` (en retard), `""` quand seules des annulées / terminées existent | non |
| BH-215 | données incohérentes | anomalies | 200 | statut inconnu (3, -1) → **`"status":null`** (notice PHP) ; plaque vide `""` triée en premier ; marque et modèle vides → `"label":" "` ; `"mileage":-500` ; fin avant début → `"08\/01\/2026 18:00"` (1-ANO-007) ; statut `CONFIRMEE` (majuscules) **ignoré** ; A5 exact : `PHP Notice: Undefined offset: 3` puis `PHP Notice: Undefined offset: -1` (observé à l'implémentation) | **oui** : notice → warning (PHP 8) ; `null` |
| BH-216 | flotte réaliste | nominal | 200 | 24 éléments, golden + empreinte (3 124 octets, empreinte `1d3add568c40…`, observé à l'implémentation) | oui : comme BH-201 |
| BH-217 | volume | volume | 200 | 500 éléments, 58 504 octets ; **~300 ms** en step-00, contre ~40 ms pour la liste HTML du même volume (une requête SQL par véhicule pour charger ses réservations : N+1) ; suite `diff` : temps `app` ≤ 2 × `app-ref` | oui : performance, chargement paresseux (ORM) |
| BH-218 | `GET /api/vehicules?status=1&callback=x` | demo | 200 | paramètres **ignorés** : corps identique à BH-201 (pas de filtre, pas de JSONP) | non |
| BH-219 | `Accept: application/xml` ; `Origin: https://planning.example` | demo | 200 | aucune négociation de contenu (JSON quand même), aucun en-tête CORS | oui : si un composant CORS est ajouté |
| BH-220 | `HEAD /api/vehicules` | demo | 200 | corps vide, `Content-Type: application/json` | non |
| BH-221 | `POST` et `OPTIONS /api/vehicules` | demo | 405 | `Allow: GET, HEAD` ; corps **HTML** (pas de JSON d'erreur) ; `Content-Type: text/html; charset=UTF-8`, `X-Fleet-Version`, `Cache-Control: no-cache` (ACC-001 à partir de step-01 : `no-cache, private`), **pas** de `Set-Cookie` (observé à l'implémentation) | oui : page d'erreur |
| BH-222 | `GET /api/vehicules/`, `/api/vehicules.json`, `/api/vehicules/1` | demo | 404 | page HTML ; aucune autre route d'API ; mêmes en-têtes que BH-221, pas de `Location`, pas de `Set-Cookie` (observé à l'implémentation) | oui : routage |
| BH-223 | Basic `gestionnaire:mauvais` | demo | **401** | `WWW-Authenticate: Basic realm="Secured Area"` : de mauvais identifiants bloquent l'API pourtant publique ; corps et `Content-Type: text/html; charset=UTF-8`, **pas** de `Set-Cookie` (observé à l'implémentation) | **oui** : authenticators (step-06) |
| BH-224 | Basic `gestionnaire:flotte2016` | demo | 200 | corps identique à BH-201, **mais** `Set-Cookie: PHPSESSID` (session ouverte par l'authentification) ; ACC-001 à partir de step-01 : `Cache-Control: max-age=0, must-revalidate, private` + `Expires` | **oui** : firewall sans état ou non (step-06) |

### 4.3 Exemple complet : BH-206

```markdown
### BH-206 — L'API échappe le HTML, les guillemets et tout caractère non ASCII
- Route / interface : api_vehicles · GET /api/vehicules
- Suite : http (+ diff)            Groupe : zone-api-vehicles
- Scénario de données : edge
- Authentification : aucune
- Sensible à la migration : oui, très → l'encodage dépend des options de `JsonResponse`
  (`JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT` en 2.8) ; une autre combinaison (ou
  `JSON_UNESCAPED_UNICODE` / `JSON_UNESCAPED_SLASHES`) change les octets sans changer le sens.
- Entrée : aucune
- Attendu (step-00) :
  - A1 statut : 200
  - A2 en-têtes : Content-Type application/json · X-Fleet-Version 1.4.2 · pas de Set-Cookie
  - A3 corps : octet par octet = golden `api-vehicles/edge.json` (3 458 octets, empreinte 6f2bebf09564…) ;
    élément id 18 : label exactement (notation {uXXXX} = séquence d'échappement JSON, cf. note du § 4.1)
    "Citro{u00eb}n {u003C}b{u003E}C3{u003C}\/b{u003E} {u0022}Aircross{u0022} {u0026} {u003C}script{u003E}alert({u0027}x{u0027}){u003C}\/script{u003E}" ;
    hexadécimal majuscule pour < > & ' ", minuscule pour les caractères non ASCII ; corps 100 % ASCII ;
    après json_decode : label = `Citroën <b>C3</b> "Aircross" & <script>alert('x')</script>`
  - A4 base : inchangée
  - A5 journal PHP : aucune entrée (comparé en suite diff uniquement, proposition § 2.5)
- Golden : tests/golden/http/api-vehicles/edge.json (+ edge.json.sha256 : 6f2bebf0956404a0bd189bfad551eb43a69ac8189d4c135596853141946a2a49)
- Tests : ApiVehiclesTest::bh206_html_quotes_and_non_ascii_are_escaped (http),
  JsonEncodingTest::bh206_hex_escapes_for_html_quotes_slashes_and_non_ascii (edge),
  ApiVehiclesDiffTest::bh206_escaping_same_as_reference (diff)
```

### 4.4 Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| Octets exacts (échappements `\/`, `\uXXXX`, &#92;u003C) ou seulement JSON équivalent ? | **Octets exacts** : le client externe n'est pas sous notre contrôle ; un changement d'encodage est un changement accepté à consigner |
| `nextReservation` qui renvoie une réservation **passée** (BH-209) | Préserver (bug figé), correction éventuelle dans une étape séparée, en prévenant le client du planning |
| `"status": null` pour un statut inconnu (BH-215) | Préserver le `null` ; la notice PHP devient warning en PHP 8 sans changer le corps |
| 401 avec de mauvais identifiants (BH-223), cookie de session après authentification (BH-224) | Préserver tant que le client ne s'authentifie pas ; à revoir à step-06 (nouveau système de sécurité) |
| Performance N+1 (BH-217) | Seuil ≤ 2 × la référence ; l'optimisation (jointure) est un changement interne, autorisé s'il ne change aucun octet |
| Erreurs HTML sur une route d'API (BH-221, BH-222) | Corps non contractuel ; une page d'erreur JSON future serait un changement accepté |

> Toutes les propositions ci-dessus sont appliquées **à titre provisoire** par les tests (2026-11-17), en attente de
> décision de l'utilisateur ; aucune ne bloque une fiche. Voir le journal d'implémentation.

## 5. `AdminController` — ajout d'un véhicule (HTTP Basic)

> **Objet** : appliquer le gabarit commun (§ 1) et le parcours avec
> formulaire (§ 3.1 : GET du formulaire, jeton CSRF, POST, suivi de la
> redirection, diff exact de la base) à l'unique route d'administration : l'ajout d'un véhicule, **protégé par
> authentification HTTP Basic**. C'est la route la plus exposée à la migration : le système de sécurité de Symfony
> est entièrement remplacé entre 2.8 et 6.4 (`security.context`, `anonymous`, `http_basic`, encodeur `plaintext` →
> authenticators, password hasher).
>
> Valeurs observées sur l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), soumissions complètes avec et
> sans identifiants, scénarios `demo` et `empty`, comparaison de la base avant / après (2026-11-17).

### 5.1 Ce qui change par rapport aux gabarits précédents

- **Authentification dans l'Arrange** : chaque fiche précise les identifiants envoyés **au GET** du formulaire et
  **au POST** (en-tête `Authorization: Basic …`, ou rien). La session ouverte par l'authentification fait partie du
  comportement observé (voir BH-336, BH-337).
- **A2 sécurité** : sur un refus, `401` + `WWW-Authenticate: Basic realm="Secured Area"` (et corps vide côté
  contrat) ; sur un succès, `Set-Cookie: PHPSESSID`.
- **A4** : diff exact de la table `vehicle` ; `created_at` est la seule valeur **non déterministe** (horloge du
  conteneur qui démarre au 15/01/2026 10:00 puis avance) : asserter le format `Y-m-d H:i:s` et la date
  `2026-01-15`, **jamais les minutes ni les secondes**. Identifiant attendu : `max(id) + 1` (6 sur `demo`, 1 sur
  `empty`).
- **A5** : les erreurs 500 ne sont journalisées nulle part en step-00 (pas de MonologBundle) : on les constate par la
  réponse et par la base (inchangée, transaction annulée).

Rappels observés :

- identifiants : utilisateur en mémoire `gestionnaire` / `flotte2016`, rôle `ROLE_ADMIN` ;
- formulaire `vehicle` (page `<h1>Nouveau véhicule</h1>`, **aucun bouton de soumission**) :

  | Champ | Rendu | Valeur par défaut |
  |---|---|---|
  | `vehicle[plate]` | `<input type="text" required>` | — |
  | `vehicle[brand]` | `<input type="text" required>` | — |
  | `vehicle[model]` | `<input type="text" required>` | — |
  | `vehicle[mileage]` | `<input type="number" required>` | `0` |
  | `vehicle[status]` | `<select>` : `0` Disponible (**sélectionné**), `1` En entretien, `2` Hors service | `0` |
  | `vehicle[_token]` | caché | jeton CSRF |

- succès → 302 vers `/vehicules/{id}`, flash « Véhicule 1-XYZ-789 ajouté par gestionnaire. » ; plaque **normalisée**
  avant enregistrement (`PlateFormatter`), validation `BelgianPlate` sur la saisie **normalisée** ;
- le layout affiche le lien « Ajouter un véhicule » dans `<nav>` une fois authentifié ;
- en-têtes de cache : `Cache-Control: no-cache` en step-00 ; **ACC-001 à partir de step-01** : toute réponse de la
  zone qui ouvre ou utilise la session (401 comprises) en `max-age=0, must-revalidate, private` + `Expires`, le 405
  (BH-328) en `no-cache, private` (§ 1.2.6).

### 5.2 `admin_vehicle_new` — `GET|POST /admin/vehicules/nouveau`

Entrée par défaut des POST (sauf mention) : `plate=1-XYZ-789`, `brand=Renault`, `model=Master`, `mileage=1500`,
`status=0`, jeton valide, Basic `gestionnaire:flotte2016` au GET et au POST.

| Fiche | Entrée | Scénario | A1 | A2 / A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-300 | `GET` sans identifiants | demo | 401 | `WWW-Authenticate: Basic realm="Secured Area"` ; corps **vide** (`Content-Length: 0`), `Content-Type: text/html; charset=UTF-8`, `X-Fleet-Version`, `Cache-Control: no-cache` (observé à l'implémentation ; mêmes en-têtes pour BH-301 et BH-329) ; **ACC-001 à partir de step-01** : la 401 ouvre la session (BH-336) → `Cache-Control: max-age=0, must-revalidate, private` + `Expires` | **oui** : `http_basic` → authenticator (step-06) |
| BH-301 | `GET`, mot de passe faux ; utilisateur inconnu `admin` ; mot de passe vide | demo | 401 | même en-tête | **oui** : idem |
| BH-302 | `GET`, utilisateur **`Gestionnaire`** ou **`GESTIONNAIRE`** | demo | **200** | le nom d'utilisateur est **insensible à la casse** (fournisseur en mémoire) | **oui** : `InMemoryUserProvider` (step-06) |
| BH-303 | `GET`, identifiants valides | demo | 200 | formulaire complet (tableau du § 5.1), kilométrage `0` et statut « Disponible » pré-remplis, lien « Ajouter un véhicule » dans `<nav>`, **pas de bouton**, cookie `PHPSESSID` | oui : rendu des formulaires (step-01→04) |
| BH-304 | POST nominal | demo | 302 | `Location: /vehicules/6` ; flash « Véhicule 1-XYZ-789 ajouté par gestionnaire. » ; A4 : +1 ligne `vehicle` id 6 `('1-XYZ-789', 'Renault', 'Master', 1500, 0, '2026-01-15 hh:mm:ss')` | **oui** : `security.context`, `new VehicleType()`, `isValid()` sans `isSubmitted()` (step-01/02) |
| BH-305 | POST nominal sur base vide | empty | 302 | `Location: /vehicules/1`, id 1 | non |
| BH-306 | plaque `1-xyz-789` ; ` 1 xyz 789 ` ; `1.XYZ.789` ; `1xyz789` | demo | 302 | toujours enregistrée **`1-XYZ-789`** (normalisation : majuscules, sans espaces, points ni tirets, puis `N-LLL-NNN`) | oui : `PlateFormatter` (regex), PHP 8 |
| BH-307 | plaque `ABC-123` ; `1-AB-123` ; `X-XYZ-789` | demo | 200 | `La plaque "ABC-123" n'est pas une plaque belge valide (format 1-ABC-123).` (la **saisie brute** est citée) ; base inchangée ; **erreur de champ** dans le `<div>` de `plate` (avant l'`<input>`), saisie réaffichée `value="ABC-123"`, aucune erreur globale (observé à l'implémentation ; même emplacement pour BH-308, BH-311, BH-315, BH-317) | oui : messages de validation |
| BH-308 | plaque vide `""` ou `"   "` | demo | 200 | `This value should not be blank.` (anglais ; la saisie est rognée) | oui : `NotBlank`, `trim` |
| BH-309 | plaque **déjà existante** `1-ABC-123`, ou `1abc123` (même plaque une fois normalisée) | demo | **500** | page d'erreur générique ; **base inchangée** (index unique SQLite, aucune contrainte `UniqueEntity`) | **oui** : exception DBAL différente (step-06), 500 à préserver ou non (§ 5.4) |
| BH-310 | même POST valide envoyé **deux fois** | demo | 302 puis **500** | 1re : créé ; 2e : doublon → 500 ; **une seule** ligne | **oui** : idem |
| BH-311 | marque et modèle vides | demo | 200 | `This value should not be blank.` ×2 | non |
| BH-312 | marque et modèle avec espaces autour (`  Renault  `, ` Master `) | demo | 302 | enregistrés **rognés** | oui : option `trim` |
| BH-313 | marque de **51** caractères, modèle de **60** | demo | 302 | **acceptés et enregistrés en entier** (colonnes VARCHAR(50), SQLite ne contrôle pas, aucune contrainte `Length`) | **oui** : DBAL 3 / validation |
| BH-314 | marque `<b>Citroën</b>`, modèle `"C3" & 'Aircross' 🚐` | demo | 302 | enregistrés tels quels ; échappés à l'affichage (fiche, liste) et dans l'API (cf. BH-021, BH-049, BH-206) | oui : échappement |
| BH-315 | kilométrage `-1` | demo | 200 | `This value should be greater than or equal to 0.` | non |
| BH-316 | kilométrage `0` | demo | 302 | accepté (`mileage 0`) | non |
| BH-317 | kilométrage `abc` ; `99999999999999999999` | demo | 200 | `This value is not valid.` | oui : `IntegerType`, ICU |
| BH-318 | kilométrage `1500,7` | demo | 302 | **tronqué à 1500** | **oui** : `IntegerType` (arrondi, locale `fr`) |
| BH-319 | kilométrage `1e3` | demo | 302 | **enregistré 1000** | **oui** : idem |
| BH-320 | kilométrage `2147483648` (2^31) | demo | 302 | accepté et enregistré tel quel | oui : entiers 32/64 bits, DBAL |
| BH-321 | kilométrage **vide** | demo | **500** | base inchangée (kilométrage NULL → `NOT NULL` violé ; cause déduite, aucune trace de journal) | **oui** : erreur différente attendue (step-05/06) |
| BH-322 | statut `2` | demo | 302 | `status 2` (INTEGER) | non |
| BH-323 | statut `3` ; `abc` | demo | 200 | `This value is not valid.` (choix inexistant) ; erreur de champ dans le `<div>` du `<select>` `vehicle_status` (observé à l'implémentation ; BH-325 et BH-326 : erreur **globale** en tête du formulaire) | oui : `ChoiceType` (choices clé/libellé inversés en Symfony 3) |
| BH-324 | statut **absent** du POST | demo | **500** | base inchangée (statut NULL → `NOT NULL` violé ; cause déduite) | **oui** : `ChoiceType`, step-01 |
| BH-325 | sans jeton CSRF ; jeton faux | demo | 200 | `The CSRF token is invalid. Please try to resubmit the form.` ; base inchangée | oui : CSRF |
| BH-326 | champ en trop `vehicle[createdAt]` ; `vehicle[id]` | demo | 200 | `This form should not contain extra fields.` ; **aucune** affectation | oui : `allow_extra_fields` |
| BH-327 | POST **sans corps** (authentifié) | demo | 200 | formulaire réaffiché sans erreur, rien écrit | **oui** : Symfony 4 (`isValid()` non soumis) |
| BH-328 | `PUT` (authentifié) | demo | 405 | `Allow: GET, POST, HEAD` | non |
| BH-329 | POST sans identifiants **ni session** | demo | 401 | `WWW-Authenticate` ; base inchangée | **oui** : authenticators |
| BH-330 | utilisateur `GESTIONNAIRE` puis POST | demo | 302 | flash « ajouté par **gestionnaire** » : le nom affiché est celui du compte, pas celui saisi | oui : `getUsername()` → `getUserIdentifier()` (step-06) |
| BH-331 | véhicule créé puis **fiche véhicule** | demo | 302 → 200 | `<h1>1-XYZ-789 — Renault Master</h1>`, `1.500 km`, « Disponible », lien « Réserver » ; aucune réservation : « Réservations à venir » = un seul `<li>Aucune réservation à venir.</li>`, « Historique » = `<ul>` vide (observé à l'implémentation) | oui : lien avec BH-040 |
| BH-332 | véhicule créé puis **liste** et **API** | demo | 200 | 6 lignes / 6 éléments ; `1-XYZ-789` placé à sa place dans le tri par plaque | oui : lien avec BH-010, BH-201 |
| BH-333 | GET authentifié puis **POST sans en-tête `Authorization`**, même session | demo | 302 | **accepté** : l'authentification Basic est mémorisée dans la session (pare-feu avec état) | **oui, très** : pare-feu `stateless` ou non (step-06) |
| BH-334 | GET authentifié puis `GET /vehicules` sans en-tête, même session | demo | 200 | lien « Ajouter un véhicule » **affiché** (session) | **oui** : idem |
| BH-335 | `created_at` du véhicule créé | demo | 302 | format `Y-m-d H:i:s`, type SQLite TEXT (observé à l'implémentation), date `2026-01-15` (horloge du conteneur) ; `PrePersist` ne l'écrase pas s'il était fourni (non atteignable par le formulaire, cf. BH-326) | oui : `DateTime`, ORM |
| BH-336 | `GET` refusé (401) | demo | 401 | **`Set-Cookie: PHPSESSID`** : même refusée, la requête ouvre une session (le pare-feu y mémorise l'URL demandée) | oui : session, `TargetPathTrait` (step-06) |
| BH-337 | succès | demo | 302 | session ouverte par l'authentification : `Set-Cookie: PHPSESSID` posé dès le **GET** authentifié du formulaire ; le POST réussi (302), qui renvoie ce cookie, **ne repose pas** de `Set-Cookie` (observé à l'implémentation) | oui : session |

### 5.3 Exemple complet : BH-309

```markdown
### BH-309 — Ajouter un véhicule avec une plaque existante provoque une erreur 500
- Route / interface : admin_vehicle_new · POST /admin/vehicules/nouveau
- Suite : http (+ diff)            Groupe : zone-admin-vehicle-new
- Scénario de données : demo
- Authentification : Basic gestionnaire:flotte2016 au GET et au POST
- Sensible à la migration : oui → aucune contrainte UniqueEntity : le doublon n'est détecté que par l'index unique
  SQLite au flush ; l'exception (DBALException 2.5 → UniqueConstraintViolationException DBAL 3) et la page
  d'erreur changent avec les versions.
- Entrée : plate=1-ABC-123 (puis variante 1abc123), brand=Renault, model=Master, mileage=1500, status=0, jeton valide
- Attendu (step-00) :
  - A1 statut : 500
  - A2 en-têtes : X-Fleet-Version 1.4.2 · Content-Type text/html; charset=UTF-8
  - A3 corps : non contractuel (page d'erreur générique du framework)
  - A4 base : strictement inchangée (5 véhicules, 5 réservations, empreinte du scénario demo)
  - A5 journal PHP : aucune entrée (exceptions non journalisées en step-00)
- Golden : aucun (statut + base suffisent)
- Tests : AdminVehicleNewTest::bh309_existing_plate_is_a_server_error (http),
  AdminVehicleNewDiffTest::bh309_duplicate_plate_same_as_reference (diff)
```

Note : le mot de passe du compte de test est lu par les tests dans la variable d'environnement
`FLEET_ADMIN_PASSWORD` (repli : valeur de step-00), via `Support\AdminVehicleForm::adminPassword()`.

### 5.4 Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| BH-309 / BH-310 / BH-321 / BH-324 (500 sur doublon, kilométrage vide, statut absent) : figer le 500 ? | Figer **statut 500 + base inchangée** ; corriger plus tard (message de formulaire) dans une étape séparée, après décision |
| BH-302 / BH-330 (nom d'utilisateur insensible à la casse, nom du compte affiché) : préserver ? | Oui : c'est le comportement de connexion vu par l'utilisateur ; à vérifier à step-06 |
| BH-333 / BH-334 (authentification mémorisée en session) : préserver ? | Oui pendant la migration ; le passage à un pare-feu sans état serait un changement accepté (et une amélioration de sécurité à proposer) |
| Mot de passe `flotte2016` en clair (`plaintext`) dans la configuration | Hors contrat de test ; **vulnérabilité de configuration** à tracer par `/vuln-audit` (CFG), le mot de passe est passé par variable d'environnement dans les tests |
| BH-313 (marque / modèle > 50 caractères acceptés) : préserver ? | Oui (données réellement enregistrées en step-00) ; une contrainte `Length` future est un changement accepté |
| BH-318 / BH-319 (`1500,7` → 1500, `1e3` → 1000) | Préserver, comme BH-164 / BH-167 pour le retour de véhicule |

> Toutes les propositions ci-dessus sont appliquées **à titre provisoire** par les tests (2026-11-17), en attente de
> décision de l'utilisateur ; aucune ne bloque une fiche. Voir le journal d'implémentation.

## 6. Commandes console — `fleet:report`, `fleet:demo:load`

> **Objet** : appliquer le gabarit commun (§ 1) aux 2 commandes de
> l'application : `fleet:report` (rapport mensuel) et `fleet:demo:load` (rechargement destructif des données de
> démonstration). Ce sont des interfaces utilisées par des personnes et, potentiellement, par des scripts
> planifiés (cron) qui lisent la sortie et le code de retour : leur texte exact est contractuel.
>
> Valeurs observées sur l'application step-00 réelle (PHP 7.2.34 / Symfony 2.8.52), via
> `.claude/bin/console.sh` (`app/console`, sans terminal), scénarios `report-months`, `edge`, `anomalies`, `demo`,
> `volume`, `empty`, `nominal`, le 2026-11-17.

### 6.1 Ce qui change par rapport au gabarit HTTP

| Vérification | Pour une commande |
|---|---|
| **Act** | une exécution, **sans terminal** (pas de TTY : aucune couleur ANSI), dans le conteneur de la cible : `CONSOLE_APP_CMD` / `CONSOLE_REF_CMD` (`docker exec -i flotte-mig-app[-ref] sf-console …`), arguments **exactement** ceux de la fiche ; horloge du conteneur (démarre au 15/01/2026 10:00) |
| **A1** | **code de retour** (0, 1…) |
| **A2** | **sortie d'erreur** (stderr) : vide, ou message attendu (bloc d'exception du framework : seule la ligne du message est contractuelle, extraite quel que soit l'habillage — titre `[Classe]` en 2.8, localisation `In <fichier> line <n>:` en 3.4 ; `ConsoleRun::exceptionMessage`) |
| **A3** | **sortie standard** comparée **octet par octet** au golden (titre, largeur des colonnes du tableau, séparateurs de milliers, `€`, fins de ligne `\n`) + assertions ciblées (ordre des lignes, totaux) |
| **A4** | `fleet:report` : base **inchangée** ; `fleet:demo:load` : base **égale au scénario `demo`** (empreinte `tests/fixtures/db/demo.sqlite.sha256`, même dump canonique que `.claude/bin/seed.sh demo --verify`) |
| **A5** | journal PHP (les warnings deviennent des exceptions en environnement `dev`, cf. BH-416) |

Rappels observés communs :

- environnement par défaut **`dev`** avec débogage (`app/console`) : un warning PHP **interrompt** la commande
  (`ContextErrorException`, code 1) ; avec `--env=prod`, le même warning est ignoré et la commande va au bout ;
- messages applicatifs écrits sur la **sortie standard**, même les erreurs (balise `<error>`) ; exceptions et erreurs
  de syntaxe de la ligne de commande sur la **sortie d'erreur** ;
- aucune couleur sans terminal ; avec `--ansi`, le titre **et les en-têtes du tableau** sont en vert (`\e[32m…\e[39m`,
  observé à l'implémentation, BH-422) ;
- `-q` : aucune sortie, même code de retour.

### 6.2 `fleet:report` — rapport mensuel d'utilisation

`fleet:report [-m|--month=AAAA-MM]` ; défaut = **mois courant calculé au démarrage de la commande**
(`date('Y-m')` → `2026-01` avec l'horloge des conteneurs). Prend les réservations **terminées** dont la **date de
fin** est dans `[1er du mois 00:00:00, 1er du mois suivant 00:00:00[`, regroupées par plaque (nombre, km, coût),
triées par km décroissants (`usort` avec comparateur booléen).

| Fiche | Commande | Scénario | A1 | A2 / A3 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-400 | `fleet:report --month=2016-05` | demo | 0 | exemple du README, sortie exacte : `1-BCD-234 2 450 157,50 €`, `1-ABC-123 1 210 73,50 €`, `2-EFG-567 1 157 54,95 €`, `TOTAL 817 285,95 €` | oui : tableau `Table` (console 2.8 → 6.4) |
| BH-401 | `fleet:report --month=2026-01` | report-months | 0 | 7 lignes + TOTAL `1.707` km / `597,45 €` ; tableau au format `+---+` / `\| … \|` ; titre `Rapport de flotte - 2026-01` | **oui** : rendu de `Table` (bordures, alignement, largeur Unicode du `€`) |
| BH-402 | `fleet:report` (sans option) | report-months | 0 | **identique** à BH-401 : le défaut suit l'horloge (2026-01) | oui : défaut calculé à `configure()` |
| BH-403 | `fleet:report -m 2025-11` ; `-m2025-11` ; `--month 2025-11` | report-months | 0 | trois syntaxes équivalentes, sortie identique | oui : analyse des options |
| BH-404 | `fleet:report --month=2025-11` | report-months | 0 | **ex æquo à 300 km** : ordre observé `1-REP-001`, `1-REP-002`, `1-REP-003` (2 réservations), puis `1-REP-008` (3 km), `1-REP-004` (1 km) ; TOTAL `904` / `316,40 €` | **oui, très** : `usort` booléen, tri stable de PHP 8 (step-05) |
| BH-405 | `fleet:report --month=2026-01` | report-months | 0 | **ex æquo à 250 km** : `1-REP-002`, `1-REP-003`, `1-REP-004` (3 réservations) | **oui, très** : idem |
| BH-406 | `fleet:report --month=2026-01` | edge | 0 | `1-LNG-040` (265 km) puis **4 ex æquo à 100 km** `1-TIE-001` … `1-TIE-004` ; TOTAL `665` / `232,75 €` | **oui, très** : idem |
| BH-407 | `fleet:report --month=2025-12` | report-months | 0 | mois **sans donnée** : tableau réduit à l'en-tête et `TOTAL 0 0,00 €` | oui : `Table` vide |
| BH-408 | `fleet:report --month=2026-01` | report-months | 0 | bornes : réservation finissant **le 01/01/2026 à 00:00:00** comptée (900 km) ; celle **à cheval sur le Nouvel An** comptée (44 km) ; **dernière seconde** de janvier comptée (13 km) ; celle finissant le **01/02/2026 à 00:00:00 exclue** (`1-REP-008`, 101 km, présente en février — observé à l'implémentation) | oui : comparaison de dates (DQL, DBAL) |
| BH-409 | `fleet:report --month=2026-02` | report-months | 0 | la réservation exclue de janvier y figure (101 km) ; très grands nombres `999.999` km, `349 999,65 €`, TOTAL `1.000.112` / `350 039,20 €` | oui : `number_format`, flottants |
| BH-410 | `fleet:report --month=2025-10` et `--month=2026-03` | report-months | 0 | réservations autour des **changements d'heure** comptées (heures naïves) ; 7 km = `2,45 €` (`2-REP-007`, octobre 2025 : 5 lignes, TOTAL `761` / `266,35 €` ; mars 2026 : 2 lignes, TOTAL `95` / `33,25 €` — observé à l'implémentation) | oui : `DateTime`, fuseau |
| BH-411 | `fleet:report --month=2024-02` et `--month=2024-03` | report-months | 0 | **29 février 2024** compté en février ; réservation du 29/02 au 01/03 08:00 comptée en **mars** (date de fin) ; février 2024 : une ligne `1-REP-001 1 120 42,00 €`, mars 2024 : une ligne `1-REP-002 1 80 28,00 €` (observé à l'implémentation) | non |
| BH-412 | `fleet:report --month=2025-13` | report-months | 0 | **accepté** : titre `Rapport de flotte - 2025-13` mais **données de janvier 2026** (débordement de `createFromFormat`) | **oui** : `DateTime::createFromFormat` (PHP 8) |
| BH-413 | `fleet:report --month=2026-00` ; `2016-13` ; `0000-01` ; `9999-12` | report-months | 0 | acceptés ; `2026-00` = décembre 2025 (vide ici) ; `2016-13` = janvier 2017 (tableau identique, observé à l'implémentation) ; tableaux vides | oui : idem |
| BH-414 | `fleet:report --month=abc` ; `2026-1` ; `26-01` ; `2026/01` ; `2026-01-15` ; `" 2026-01"` ; `"2026-01 "` | report-months | **1** | **sortie standard** (pas d'erreur) : `Format de mois invalide (attendu : AAAA-MM).` ; stderr vide | oui : sortie `<error>` |
| BH-415 | `fleet:report --month=` | report-months | **1** | stderr : `The "--month" option requires a value.` + rappel de syntaxe ; stdout vide | **oui** : messages et format des erreurs de console (step-02/06) |
| BH-416 | `fleet:report --month=2026-01` | anomalies | **1** | env `dev` : stderr `[Symfony\Component\Debug\Exception\ContextErrorException]` `Warning: A non-numeric value encountered` (coût `"N/A"`), **stdout vide** ; A5 : **aucune** entrée au journal PHP (observé à l'implémentation) ; contractuel : la **ligne de message** seule, habillage hors contrat (step-01 : `In ReservationManager.php line 98:` puis le même message, observé le 2026-11-17) | **oui, très** : PHP 8 lève `TypeError` (`Unsupported operand types`), composant Debug → ErrorHandler |
| BH-417 | `fleet:report --month=2026-01 --env=prod` | anomalies | 0 | rapport produit : `1-ANO-001 1 100 35,00 €`, `1-ANO-008 2 20 12,35 €` (`"N/A"` compté 0, `12.345` arrondi à l'affichage), `1-ANO-006 2 -50 -35,00 €`, `1-ANO-007 1 -7.000 0,00 €` (fin sans `end_km`), TOTAL `-6.930` / `12,35 €` ; stderr vide ; A5 : **une** entrée `PHP Warning: A non-numeric value encountered` (observé à l'implémentation) | **oui, très** : idem, nombres négatifs |
| BH-418 | `fleet:report --mois=2026-01` | demo | **1** | stderr : `The "--mois" option does not exist.` | oui : messages console |
| BH-419 | `fleet:report 2026-01` | demo | **1** | stderr : `Too many arguments, expected arguments "command".` | oui : idem |
| BH-420 | `fleet:report --month=2026-01 -q` | report-months | 0 | **aucune** sortie | non |
| BH-421 | `fleet:report --month=2026-01 --env=prod` | report-months | 0 | sortie **identique** à BH-401 (dev) quand les données sont saines | oui : environnements |
| BH-422 | `fleet:report --month=2026-01 --ansi` | report-months | 0 | titre entouré de `\e[32m` … `\e[39m` ; **les 4 cellules d'en-tête du tableau aussi** (`\|\e[32m Plaque    \e[39m\|…`, espaces de remplissage compris), lignes de données et TOTAL sans couleur (10 séquences ESC au total) ; sans les séquences, sortie identique à BH-401 (observé à l'implémentation) | oui : styles de sortie |
| BH-423 | `fleet:report --month=2024-03` | volume | 0 | 172 véhicules, TOTAL `187.606` km / `65 662,10 €` ; ~130 ms en step-00 ; suite `diff` : durée `app` ≤ 2 × `app-ref` | oui : performance |
| BH-424 | toute exécution de `fleet:report` | report-months | 0 | **base inchangée** (lecture seule) | non |
| BH-425 | `list` ; `help fleet:report` | demo | 0 | `fleet:report` décrite « Rapport mensuel d'utilisation de la flotte » ; option `-m, --month=MONTH Mois au format AAAA-MM [default: "2026-01"]` (défaut dépendant de l'horloge : à normaliser) ; **ACC-002 à partir de step-01** : seules les lignes `fleet:*` (nom, description, synopsis `fleet:report [options]`, option `--month` et son défaut, aide) sont contractuelles, en-tête de version et options globales non vérifiés | oui : rendu de l'aide (step-02/06) |

### 6.3 `fleet:demo:load` — rechargement des données de démonstration

**Destructive et sans confirmation** : supprime toutes les réservations puis tous les véhicules (DQL `DELETE`), puis
insère 5 véhicules (créés le `2016-01-04 08:00:00`) et 5 réservations ; les identifiants repartent à 1.
Sortie : `Données de démonstration chargées : 5 véhicules, 5 réservations.` (code 0).

| Fiche | Commande | Scénario | A1 | A2 / A3 / A4 — points clés (observés) | Sensible |
|---|---|---|---|---|---|
| BH-450 | `fleet:demo:load` | empty | 0 | stdout exact (une ligne) ; stderr vide ; **base = scénario `demo`** (empreinte identique, types SQLite compris) | **oui** : `ContainerAwareCommand`, DQL `DELETE` (step-02/06) |
| BH-451 | `fleet:demo:load` | nominal | 0 | 24 véhicules et 476 réservations **supprimés sans demande** ; base = `demo` ; ids repartent à 1 | oui : idem |
| BH-452 | `fleet:demo:load` | anomalies | 0 | données incohérentes supprimées sans erreur ; base = `demo` | oui : idem |
| BH-453 | `fleet:demo:load` | edge | 0 | 75 réservations supprimées (y compris celles des véhicules aux plaques non normalisées) ; base = `demo` | non |
| BH-454 | `fleet:demo:load` **deux fois** de suite | demo | 0, 0 | même sortie, base = `demo` : commande **idempotente** | non |
| BH-455 | `fleet:demo:load -n` (non interactif) | demo | 0 | même comportement : aucune question n'est jamais posée | oui : si une confirmation est ajoutée |
| BH-456 | `fleet:demo:load -q` | nominal | 0 | **aucune** sortie, base = `demo` quand même | non |
| BH-457 | `fleet:demo:load --env=prod` | nominal | 0 | **exécutée aussi en production** (aucun garde-fou d'environnement), base = `demo` | **oui** : un garde-fou futur serait un changement accepté |
| BH-458 | `fleet:demo:load extra` | demo | **1** | stderr : `Too many arguments, expected arguments "command".` ; **base inchangée** | oui : messages console |
| BH-459 | `help fleet:demo:load` ; `list` | demo | 0 | description `Vide la base et charge les donnees de demonstration` (**sans accents**, comme dans le code) ; **ACC-002 à partir de step-01** : seules les lignes `fleet:demo:load` (synopsis, aucune option propre, description) sont contractuelles | oui : rendu de l'aide |
| BH-460 | après `fleet:demo:load` : `GET /vehicules`, `GET /api/vehicules`, `fleet:report --month=2016-05` | nominal | 0 / 200 | mêmes sorties que BH-010, BH-201 et BH-400 | oui : liens avec ces fiches |

### 6.4 Exemple complet : BH-404

```markdown
### BH-404 — Le rapport ordonne les véhicules ex æquo dans leur ordre d'apparition (tri instable figé)
- Interface : console · fleet:report --month=2025-11
- Suite : cli (+ edge, + diff)            Groupe : zone-cli-report
- Scénario de données : report-months
- Sensible à la migration : oui, très → usort() avec un comparateur qui renvoie un booléen
  ($a['km'] < $b['km']) : PHP 8.0 (step-05) rend le tri stable et déprécie ce comparateur ; l'ordre des ex æquo
  peut changer, puis le comparateur booléen sera refusé dans une version future.
- Entrée : argument --month=2025-11, aucun terminal
- Attendu (step-00) :
  - A1 code de retour : 0
  - A2 stderr : vide
  - A3 stdout : golden `cli/report/2025-11.txt`, exactement :
      Rapport de flotte - 2025-11
      +-----------+--------------+-----+----------+
      | Plaque    | Réservations | Km  | Coût     |
      +-----------+--------------+-----+----------+
      | 1-REP-001 | 1            | 300 | 105,00 € |
      | 1-REP-002 | 1            | 300 | 105,00 € |
      | 1-REP-003 | 2            | 300 | 105,00 € |
      | 1-REP-008 | 1            | 3   | 1,05 €   |
      | 1-REP-004 | 1            | 1   | 0,35 €   |
      | TOTAL     |              | 904 | 316,40 € |
      +-----------+--------------+-----+----------+
    assertions ciblées : ordre des plaques ; TOTAL 904 / 316,40 €
  - A4 base : inchangée
  - A5 journal PHP : aucune entrée en step-00 (en PHP 8 : dépréciation du comparateur booléen attendue)
- Golden : tests/golden/cli/report/2025-11.txt (stdout brut, octet par octet ; partagé avec BH-403)
- Tests : FleetReportTest::bh404_ties_at_300_km_keep_observed_order (cli),
  ReportOrderingTest::bh404_three_ties_at_300_km (edge, 3 exécutions : ordre déterministe),
  FleetReportDiffTest::bh404_tie_order_at_300_km_same_as_reference (diff)
```

### 6.5 Points à trancher avant d'écrire les tests

| Question | Proposition |
|---|---|
| Ordre des ex æquo (BH-404 à BH-406) : figer l'ordre observé ? | **Oui** : le migrator devra rendre l'ordre explicite (même résultat qu'en PHP 7.2), sans s'appuyer sur la stabilité du tri |
| `--month=2025-13` accepté avec les données du mois suivant (BH-412, BH-413) | Préserver pendant la migration ; une validation stricte du mois serait un changement accepté |
| Message d'erreur de format sur la **sortie standard** avec code 1 (BH-414) | Préserver (des scripts peuvent lire stdout) |
| BH-416 / BH-417 : comportement différent `dev` / `prod` sur données incohérentes | Figer les deux ; c'est la divergence PHP 7 / 8 la plus probable du projet |
| `fleet:demo:load` destructive, sans confirmation ni garde-fou en `prod` (BH-450 à BH-457) | Préserver le comportement ; signaler le risque (perte de données en production) comme point d'amélioration post-migration |
| Défaut de `--month` affiché dans l'aide (BH-425) | Normaliser la date dans le golden (dépend de l'horloge) |
| Rendu exact des tableaux (bordures, largeurs, caractère `€`) | Contractuel (octet par octet) ; un changement de rendu du composant `Table` est un changement accepté à consigner |

> Toutes les propositions ci-dessus sont appliquées **à titre provisoire** par les tests (2026-11-17), en attente de
> décision de l'utilisateur ; aucune ne bloque une fiche. Voir le journal d'implémentation.

## Journal d'implémentation

Une ligne par fiche implémentée (conventions § 5.6), ajoutée par les skills `write-*-tests` / `golden-master`.

| Date | Fiche | Classe::méthode | Suite(s) | Golden(s) | app-ref | Écart corrigé dans la fiche |
|---|---|---|---|---|---|---|
| 2026-11-17 | BH-001 | `Http\HomepageTest::bh001_root_redirects_to_vehicle_list` | http | — (302, corps non contractuel) | ✔ | non |
| 2026-11-17 | BH-002 | `Http\HomepageTest::bh002_root_redirects_whatever_the_method`, `Diff\HomepageDiffTest::bh002_root_redirect_for_any_method_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-003 | `Http\HomepageTest::bh003_root_redirect_drops_query_string` | http | — | ✔ | non |
| 2026-11-17 | BH-004 | `Http\HomepageTest::bh004_head_on_root_redirects_with_empty_body` | http | — | ✔ | non |
| 2026-11-17 | BH-010 | `Http\VehiclesListTest::bh010_list_sorted_by_plate_with_formatted_columns` | http | `http/vehicles-list/bh010.html` | ✔ | non |
| 2026-11-17 | BH-011 | `Http\VehiclesListTest::bh011_empty_list_shows_single_placeholder_row`, `Diff\VehiclesListDiffTest::bh011_empty_list_same_as_reference` | http, diff | `http/vehicles-list/bh011.html` | ✔ | non |
| 2026-11-17 | BH-012 | `Http\VehiclesListTest::bh012_numeric_status_filter_selects_option` | http | `http/vehicles-list/bh012-status-{0,1,2}.html` | ✔ | non |
| 2026-11-17 | BH-013 | `Http\VehiclesListTest::bh013_empty_status_filter_lists_all` | http | `http/vehicles-list/bh013.html` | ✔ | non |
| 2026-11-17 | BH-014 | `Http\VehiclesListTest::bh014_unknown_numeric_status_shows_no_vehicle` | http | `http/vehicles-list/bh014.html` | ✔ | non |
| 2026-11-17 | BH-015 | `Http\VehiclesListTest::bh015_status_filter_with_non_numeric_value`, `Edge\LooseStatusFilterTest::bh015_non_numeric_status_matches_available_vehicles`, `Diff\VehiclesListDiffTest::bh015_non_numeric_status_same_as_reference` | http, edge, diff | `http/vehicles-list/bh015-status-abc.html` | ✔ | non (exemple § 2.4 aligné : nom du golden, A5 en diff) |
| 2026-11-17 | BH-016 | `Http\VehiclesListTest::bh016_status_filter_with_numeric_prefix_string`, `Edge\LooseStatusFilterTest::bh016_numeric_prefix_string_matches_status_zero`, `Diff\VehiclesListDiffTest::bh016_numeric_prefix_status_same_as_reference` | http, edge, diff | `http/vehicles-list/bh016-status-0abc.html` | ✔ | non |
| 2026-11-17 | BH-017 | `Http\VehiclesListTest::bh017_status_filter_with_leading_zero_or_space`, `Edge\LooseStatusFilterTest::bh017_leading_zero_and_leading_space_match_status_one`, `Diff\VehiclesListDiffTest::bh017_leading_zero_and_space_status_same_as_reference` | http, edge, diff | `http/vehicles-list/bh017-status-01.html`, `bh017-status-space-1.html` | ✔ | non |
| 2026-11-17 | BH-018 | `Http\VehiclesListTest::bh018_status_filter_with_array_value`, `Edge\LooseStatusFilterTest::bh018_array_status_matches_nothing_and_never_fails`, `Diff\VehiclesListDiffTest::bh018_array_status_same_as_reference` | http, edge, diff | `http/vehicles-list/bh018-status-array.html` | ✔ | non |
| 2026-11-17 | BH-019 | `Http\VehiclesListTest::bh019_binary_plate_order_with_normalized_display`, `Diff\VehiclesListDiffTest::bh019_edge_plate_order_same_as_reference` | http, diff | `http/vehicles-list/bh019-edge.html` | ✔ | non |
| 2026-11-17 | BH-020 | `Http\VehiclesListTest::bh020_mileage_number_format`, `Edge\NumberFormatTest::bh020_mileage_zero_and_int32_max`, `Diff\VehiclesListDiffTest::bh020_edge_number_format_same_as_reference` | http, edge, diff | `http/vehicles-list/bh020-edge.html` | ✔ | non |
| 2026-11-17 | BH-021 | `Http\VehiclesListTest::bh021_html_escaping_and_unicode`, `Edge\HtmlEscapingTest::bh021_brand_and_model_escaped_exactly`, `Diff\VehiclesListDiffTest::bh021_edge_escaping_same_as_reference` | http, edge, diff | `http/vehicles-list/bh021-edge.html` | ✔ | non |
| 2026-11-17 | BH-022 | `Http\VehiclesListTest::bh022_legacy_anomalies_in_list`, `Edge\LegacyAnomaliesTest::bh022_unknown_status_renders_empty_label_without_error`, `Diff\VehiclesListDiffTest::bh022_anomalies_list_and_php_log_same_as_reference` | http, edge, diff | `http/vehicles-list/bh022-anomalies.html` | ✔ | oui (ordre, cellule « une espace », signatures A5 exactes) |
| 2026-11-17 | BH-023 | `Http\VehiclesListTest::bh023_admin_sees_add_vehicle_link`, `Diff\VehiclesListDiffTest::bh023_admin_list_same_as_reference` | http, diff | `http/vehicles-list/bh023-admin.html` | ✔ | oui (href `/admin/vehicules/nouveau`) |
| 2026-11-17 | BH-024 | `Http\VehiclesListTest::bh024_wrong_credentials_on_public_page_are_rejected`, `Diff\VehiclesListDiffTest::bh024_wrong_credentials_same_as_reference` | http, diff | — (corps non contractuel) | ✔ | non |
| 2026-11-17 | BH-025 | `Http\VehiclesListTest::bh025_trailing_slash_is_not_found`, `Diff\VehiclesListDiffTest::bh025_trailing_slash_same_as_reference` | http, diff | — (404, proposition § 2.5) | ✔ | non |
| 2026-11-17 | BH-026 | `Http\VehiclesListTest::bh026_post_is_method_not_allowed` | http | — (405, proposition § 2.5) | ✔ | non |
| 2026-11-17 | BH-027 | `Http\VehiclesListTest::bh027_head_returns_same_headers_without_body` | http | — | ✔ | non |
| 2026-11-17 | BH-028 | `Http\VehiclesListTest::bh028_volume_list_shows_all_vehicles`, `Diff\VehiclesListDiffTest::bh028_volume_list_content_and_performance_vs_reference` | http, diff | — (500 lignes, contenu comparé en diff) | ✔ | non |
| 2026-11-17 | BH-040 | `Http\VehicleShowTest::bh040_vehicle_page_with_upcoming_and_history`, `Diff\VehicleShowDiffTest::bh040_vehicle_page_same_as_reference` | http, diff | `http/vehicle-show/bh040.html` | ✔ | oui (libellé « (retour) ») |
| 2026-11-17 | BH-041 | `Http\VehicleShowTest::bh041_no_upcoming_and_history_in_date_order`, `Diff\VehicleShowDiffTest::bh041_history_order_same_as_reference` | http, diff | `http/vehicle-show/bh041.html` | ✔ | non |
| 2026-11-17 | BH-042 | `Http\VehicleShowTest::bh042_unavailable_vehicle_has_no_booking_link` | http | `http/vehicle-show/bh042-vehicle-{3,4}.html` | ✔ | non |
| 2026-11-17 | BH-043 | `Http\VehicleShowTest::bh043_vehicle_without_reservations_has_empty_history_list`, `Diff\VehicleShowDiffTest::bh043_empty_history_same_as_reference` | http, diff | `http/vehicle-show/bh043-edge-1.html` | ✔ | non |
| 2026-11-17 | BH-044 | `Http\VehicleShowTest::bh044_maintenance_vehicle_shows_future_booking_without_link` | http | `http/vehicle-show/bh044-edge-11.html` | ✔ | non |
| 2026-11-17 | BH-045 | `Http\VehicleShowTest::bh045_overdue_booking_listed_as_upcoming` | http | `http/vehicle-show/bh045-edge-21.html` | ✔ | non |
| 2026-11-17 | BH-046 | `Http\VehicleShowTest::bh046_same_start_bookings_keep_observed_order`, `Diff\VehicleShowDiffTest::bh046_same_start_order_same_as_reference` | http, diff | `http/vehicle-show/bh046-edge-22.html` | ✔ | non (ordre observé : réservation 61 puis 62) |
| 2026-11-17 | BH-047 | `Http\VehicleShowTest::bh047_long_lists_without_pagination` | http | `http/vehicle-show/bh047-edge-17.html` | ✔ | oui (7 à venir / 29 historique) |
| 2026-11-17 | BH-048 | `Http\VehicleShowTest::bh048_cost_rounding_in_history`, `Edge\NumberFormatTest::bh048_cost_rounding_two_decimals_with_comma`, `Diff\VehicleShowDiffTest::bh048_cost_rounding_same_as_reference` | http, edge, diff | `http/vehicle-show/bh048-edge-25.html` | ✔ | non |
| 2026-11-17 | BH-049 | `Http\VehicleShowTest::bh049_html_escaping_on_vehicle_page`, `Diff\VehicleShowDiffTest::bh049_escaping_same_as_reference` | http, diff | `http/vehicle-show/bh049-edge-18.html` | ✔ | non |
| 2026-11-17 | BH-050 | `Http\VehicleShowTest::bh050_naive_times_around_dst_changes`, `Diff\VehicleShowDiffTest::bh050_dst_times_same_as_reference` | http, diff | `http/vehicle-show/bh050-edge-24.html` | ✔ | non |
| 2026-11-17 | BH-051 | `Http\VehicleShowTest::bh051_legacy_anomalies_on_vehicle_page`, `Edge\LegacyAnomaliesTest::bh051_non_numeric_and_null_costs_render_without_error`, `Diff\VehicleShowDiffTest::bh051_anomalies_pages_and_php_log_same_as_reference` | http, edge, diff | `http/vehicle-show/bh051-anomalies-{1,6,8}.html` | ✔ | oui (pas de lien « Réserver » sur statut inconnu) |
| 2026-11-17 | BH-052 | `Http\VehicleShowTest::bh052_leading_zero_id_serves_same_page`, `Diff\VehicleShowDiffTest::bh052_leading_zero_id_same_as_reference` | http, diff | `http/vehicle-show/bh052.html` | ✔ | non |
| 2026-11-17 | BH-053 | `Http\VehicleShowTest::bh053_unknown_id_is_not_found`, `Diff\VehicleShowDiffTest::bh053_unknown_id_same_as_reference` | http, diff | — (404, proposition § 2.5) | ✔ | oui (pas de `Set-Cookie` sur 302/404/405) |
| 2026-11-17 | BH-054 | `Http\VehicleShowTest::bh054_invalid_or_huge_id_is_not_found_never_500`, `Diff\VehicleShowDiffTest::bh054_invalid_or_huge_id_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-055 | `Http\VehicleShowTest::bh055_trailing_slash_is_not_found`, `Diff\VehicleShowDiffTest::bh055_trailing_slash_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-056 | `Http\VehicleShowTest::bh056_post_is_method_not_allowed` | http | — (405) | ✔ | non |
| 2026-11-17 | BH-001…056 | Propositions § 2.5 appliquées à titre provisoire, **en attente de décision** : corps 404/405 non contractuels, A5 comparé en diff uniquement, seuil BH-028 ≤ 2 × référence, BH-015 à BH-018 / BH-024 / BH-052 figés | — | — | — | — |
| 2026-11-17 | BH-100 | `Http\ReservationNewTest::bh100_booking_form_has_three_required_fields_and_no_button`, `Diff\ReservationNewDiffTest::bh100_booking_form_same_as_reference` | http, diff | `http/reservation-new/bh100.html` | ✔ | non |
| 2026-11-17 | BH-101 | `Http\ReservationNewTest::bh101_valid_booking_is_created_and_redirects_with_flash`, `Diff\ReservationNewDiffTest::bh101_valid_booking_same_as_reference` | http, diff | `http/reservation-new/bh101-after-redirect.html` | ✔ | non |
| 2026-11-17 | BH-102 | `Http\ReservationNewTest::bh102_unavailable_vehicle_is_refused` | http | `http/reservation-new/bh102-{maintenance,out-of-service}.html` | ✔ | non |
| 2026-11-17 | BH-103 | `Http\ReservationNewTest::bh103_end_equal_to_start_is_refused` | http | `http/reservation-new/bh103.html` | ✔ | non |
| 2026-11-17 | BH-104 | `Http\ReservationNewTest::bh104_end_before_start_is_refused` | http | — | ✔ | non |
| 2026-11-17 | BH-105 | `Http\ReservationNewTest::bh105_slot_inside_existing_booking_is_refused` | http | `http/reservation-new/bh105.html` | ✔ | non |
| 2026-11-17 | BH-106 | `Http\ReservationNewTest::bh106_slot_ending_exactly_at_existing_start_is_refused`, `Diff\ReservationNewDiffTest::bh106_slot_touching_start_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-107 | `Http\ReservationNewTest::bh107_slot_starting_exactly_at_existing_end_is_refused`, `Diff\ReservationNewDiffTest::bh107_slot_touching_end_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-108 | `Http\ReservationNewTest::bh108_slot_enclosing_existing_booking_is_refused` | http | — | ✔ | non |
| 2026-11-17 | BH-109 | `Http\ReservationNewTest::bh109_one_minute_gap_is_enough` | http | — | ✔ | non |
| 2026-11-17 | BH-110 | `Http\ReservationNewTest::bh110_finished_and_cancelled_bookings_do_not_block` | http | — | ✔ | non (deux créneaux testés : annulée 10/02 09:00–13:00, terminée 12/01 09:00–13:00) |
| 2026-11-17 | BH-111 | `Http\ReservationNewTest::bh111_slot_over_cancelled_booking_is_accepted` | http | — | ✔ | non |
| 2026-11-17 | BH-112 | `Http\ReservationNewTest::bh112_slot_during_ongoing_booking_is_refused` | http | — | ✔ | non |
| 2026-11-17 | BH-113 | `Http\ReservationNewTest::bh113_overdue_booking_still_blocks_its_past_slot` | http | — | ✔ | non |
| 2026-11-17 | BH-114 | `Http\ReservationNewTest::bh114_booking_in_the_past_is_accepted` | http | — | ✔ | non |
| 2026-11-17 | BH-115 | `Http\ReservationNewTest::bh115_zero_mileage_vehicle_gives_zero_start_km` | http | — | ✔ | non |
| 2026-11-17 | BH-116 | `Http\ReservationNewTest::bh116_blank_driver_name_is_refused_after_trim`, `Diff\ReservationNewDiffTest::bh116_blank_driver_same_as_reference` | http, diff | `http/reservation-new/bh116-{empty,spaces}.html` | ✔ | oui (erreur de champ, pas globale) |
| 2026-11-17 | BH-117 | `Http\ReservationNewTest::bh117_driver_name_is_trimmed`, `Diff\ReservationNewDiffTest::bh117_trimmed_driver_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-118 | `Http\ReservationNewTest::bh118_driver_name_of_101_characters_is_stored_in_full`, `Edge\ReservationInputParsingTest::bh118_driver_name_longer_than_column_is_not_truncated`, `Diff\ReservationNewDiffTest::bh118_long_driver_same_as_reference` | http, edge, diff | — | ✔ | oui (variante multi-octets `é` ×101) |
| 2026-11-17 | BH-119 | `Http\ReservationNewTest::bh119_html_and_unicode_driver_name_stored_raw_and_displayed_escaped`, `Diff\ReservationNewDiffTest::bh119_html_driver_same_as_reference` | http, diff | `http/reservation-new/bh119-after-redirect.html` | ✔ | non |
| 2026-11-17 | BH-120 | `Http\ReservationNewTest::bh120_iso_dates_are_invalid_on_both_fields`, `Edge\ReservationInputParsingTest::bh120_iso_format_is_rejected_by_icu_pattern`, `Diff\ReservationNewDiffTest::bh120_iso_dates_same_as_reference` | http, edge, diff | `http/reservation-new/bh120.html` | ✔ | non |
| 2026-11-17 | BH-121 | `Http\ReservationNewTest::bh121_impossible_or_incomplete_dates_are_invalid`, `Edge\ReservationInputParsingTest::bh121_non_lenient_parsing_rejects_impossible_dates`, `Diff\ReservationNewDiffTest::bh121_impossible_dates_same_as_reference` | http, edge, diff | `http/reservation-new/bh121-{feb-31,hour-25,no-minutes}.html` | ✔ | non |
| 2026-11-17 | BH-122 | `Http\ReservationNewTest::bh122_two_digit_year_is_stored_as_year_26_shifted`, `Edge\ReservationInputParsingTest::bh122_two_digit_year_becomes_julian_year_26_with_brussels_solar_time`, `Diff\ReservationNewDiffTest::bh122_two_digit_year_same_as_reference` | http, edge, diff | `http/reservation-new/bh122-two-digit-year.html` | ✔ | non (exemple § 3.4 aligné : suites, nom du golden, libellé « (retour) ») |
| 2026-11-17 | BH-123 | `Http\ReservationNewTest::bh123_single_digit_date_parts_are_accepted`, `Edge\ReservationInputParsingTest::bh123_single_digit_day_month_hour_are_accepted`, `Diff\ReservationNewDiffTest::bh123_single_digits_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-124 | `Http\ReservationNewTest::bh124_seconds_are_accepted_and_dropped`, `Edge\ReservationInputParsingTest::bh124_trailing_seconds_are_accepted_and_dropped`, `Diff\ReservationNewDiffTest::bh124_seconds_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-125 | `Http\ReservationNewTest::bh125_nonexistent_dst_hour_is_invalid_on_start_only`, `Edge\ReservationInputParsingTest::bh125_nonexistent_spring_forward_hour_is_rejected`, `Diff\ReservationNewDiffTest::bh125_nonexistent_hour_same_as_reference` | http, edge, diff | `http/reservation-new/bh125.html` | ✔ | non |
| 2026-11-17 | BH-126 | `Http\ReservationNewTest::bh126_ambiguous_dst_hour_is_stored_as_is`, `Edge\ReservationInputParsingTest::bh126_ambiguous_fall_back_hour_is_stored_naively`, `Diff\ReservationNewDiffTest::bh126_ambiguous_hour_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-127 | `Http\ReservationNewTest::bh127_year_9999_is_invalid_on_both_fields`, `Edge\ReservationInputParsingTest::bh127_year_9999_is_rejected`, `Diff\ReservationNewDiffTest::bh127_year_9999_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-128 | `Http\ReservationNewTest::bh128_any_minute_and_one_minute_duration_accepted` | http | — | ✔ | non |
| 2026-11-17 | BH-129 | `Http\ReservationNewTest::bh129_missing_or_wrong_csrf_token_is_refused`, `Diff\ReservationNewDiffTest::bh129_csrf_same_as_reference` | http, diff | `http/reservation-new/bh129-missing.html` | ✔ | non |
| 2026-11-17 | BH-130 | `Http\ReservationNewTest::bh130_extra_field_inside_form_is_refused_without_assignment`, `Diff\ReservationNewDiffTest::bh130_extra_field_same_as_reference` | http, diff | `http/reservation-new/bh130.html` | ✔ | non |
| 2026-11-17 | BH-131 | `Http\ReservationNewTest::bh131_extra_field_outside_form_is_ignored` | http | — | ✔ | non |
| 2026-11-17 | BH-132 | `Http\ReservationNewTest::bh132_token_only_gives_three_mixed_language_errors`, `Diff\ReservationNewDiffTest::bh132_token_only_same_as_reference` | http, diff | `http/reservation-new/bh132.html` | ✔ | oui (trois erreurs de champ) |
| 2026-11-17 | BH-133 | `Http\ReservationNewTest::bh133_post_without_body_redisplays_form_without_error`, `Edge\ReservationInputParsingTest::bh133_post_without_body_never_fails`, `Diff\ReservationNewDiffTest::bh133_post_without_body_same_as_reference` | http, edge, diff | `http/reservation-new/bh133.html` | ✔ | non |
| 2026-11-17 | BH-134 | `Http\ReservationNewTest::bh134_same_post_twice_creates_a_single_booking` | http | — | ✔ | non |
| 2026-11-17 | BH-135 | `Http\ReservationNewTest::bh135_form_of_unavailable_vehicle_is_displayed` | http | `http/reservation-new/bh135.html` | ✔ | non |
| 2026-11-17 | BH-136 | `Http\ReservationNewTest::bh136_unknown_or_invalid_vehicle_is_not_found`, `Diff\ReservationNewDiffTest::bh136_unknown_vehicle_same_as_reference` | http, diff | — (404, proposition § 2.5) | ✔ | non |
| 2026-11-17 | BH-137 | `Http\ReservationNewTest::bh137_put_is_method_not_allowed` | http | — (405) | ✔ | non |
| 2026-11-17 | BH-138 | `Http\ReservationNewTest::bh138_created_booking_appears_in_upcoming_list_with_return_link`, `Diff\ReservationNewDiffTest::bh138_vehicle_page_after_booking_same_as_reference` | http, diff | `http/reservation-new/bh138-vehicle-page.html` | ✔ | oui (texte exact de la ligne, historique vide) |
| 2026-11-17 | BH-150 | `Http\ReservationReturnTest::bh150_return_form_has_number_field_and_no_button`, `Diff\ReservationReturnDiffTest::bh150_return_form_same_as_reference` | http, diff | `http/reservation-return/bh150.html` | ✔ | non |
| 2026-11-17 | BH-151 | `Http\ReservationReturnTest::bh151_nominal_return_closes_booking_and_updates_vehicle_mileage`, `Diff\ReservationReturnDiffTest::bh151_nominal_return_same_as_reference` | http, diff | `http/reservation-return/bh151-after-redirect.html` | ✔ | non |
| 2026-11-17 | BH-152 | `Http\ReservationReturnTest::bh152_zero_km_return_costs_nothing` | http | — | ✔ | non |
| 2026-11-17 | BH-153 | `Http\ReservationReturnTest::bh153_mileage_below_departure_is_refused` | http | `http/reservation-return/bh153.html` | ✔ | oui (erreur globale, saisie réaffichée) |
| 2026-11-17 | BH-154 | `Http\ReservationReturnTest::bh154_finished_booking_cannot_be_closed_again` | http | `http/reservation-return/bh154.html` | ✔ | non |
| 2026-11-17 | BH-155 | `Http\ReservationReturnTest::bh155_cancelled_booking_gets_the_same_already_closed_message` | http | — | ✔ | non |
| 2026-11-17 | BH-156 | `Http\ReservationReturnTest::bh156_cost_rounding_for_small_distances`, `Edge\ReturnMileageParsingTest::bh156_cost_is_rounded_float_stored_as_real`, `Diff\ReservationReturnDiffTest::bh156_cost_rounding_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-157 | `Http\ReservationReturnTest::bh157_vehicle_in_maintenance_can_be_returned` | http | — | ✔ | non |
| 2026-11-17 | BH-158 | `Http\ReservationReturnTest::bh158_vehicle_mileage_goes_backwards` | http | — | ✔ | oui (`cost 70` INTEGER) |
| 2026-11-17 | BH-159 | `Http\ReservationReturnTest::bh159_booking_not_yet_started_can_be_closed` | http | — | ✔ | non |
| 2026-11-17 | BH-160 | `Http\ReservationReturnTest::bh160_closing_one_booking_leaves_the_other_untouched` | http | — | ✔ | oui (`cost 105` INTEGER) |
| 2026-11-17 | BH-161 | `Http\ReservationReturnTest::bh161_mileage_beyond_int32_is_stored`, `Edge\ReturnMileageParsingTest::bh161_mileage_beyond_int32_is_stored_as_64_bit_integer`, `Diff\ReservationReturnDiffTest::bh161_beyond_int32_same_as_reference` | http, edge, diff | — | ✔ | oui (types INTEGER) |
| 2026-11-17 | BH-162 | `Http\ReservationReturnTest::bh162_huge_number_is_not_valid`, `Edge\ReturnMileageParsingTest::bh162_twenty_digit_number_is_not_valid`, `Diff\ReservationReturnDiffTest::bh162_huge_number_same_as_reference` | http, edge, diff | `http/reservation-return/bh162.html` | ✔ | non |
| 2026-11-17 | BH-163 | `Http\ReservationReturnTest::bh163_non_integer_text_is_not_valid`, `Edge\ReturnMileageParsingTest::bh163_text_sign_and_space_are_not_valid`, `Diff\ReservationReturnDiffTest::bh163_invalid_text_same_as_reference` | http, edge, diff | `http/reservation-return/bh163-{abc,plus,space}.html` | ✔ | non |
| 2026-11-17 | BH-164 | `Http\ReservationReturnTest::bh164_decimal_mileage_is_truncated`, `Edge\ReturnMileageParsingTest::bh164_decimals_are_truncated_down`, `Diff\ReservationReturnDiffTest::bh164_decimal_truncation_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-165 | `Http\ReservationReturnTest::bh165_thousands_dot_is_read_as_ten`, `Edge\ReturnMileageParsingTest::bh165_thousands_dot_reads_as_ten`, `Diff\ReservationReturnDiffTest::bh165_thousands_dot_same_as_reference` | http, edge, diff | `http/reservation-return/bh165.html` | ✔ | non |
| 2026-11-17 | BH-166 | `Http\ReservationReturnTest::bh166_leading_zero_is_accepted`, `Edge\ReturnMileageParsingTest::bh166_leading_zero_reads_as_decimal`, `Diff\ReservationReturnDiffTest::bh166_leading_zero_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-167 | `Http\ReservationReturnTest::bh167_exponent_notation_is_read_as_ten_thousand`, `Edge\ReturnMileageParsingTest::bh167_exponent_reads_as_ten_thousand`, `Diff\ReservationReturnDiffTest::bh167_exponent_same_as_reference` | http, edge, diff | — | ✔ | oui (types, kilométrage du véhicule inchangé) |
| 2026-11-17 | BH-168 | `Http\ReservationReturnTest::bh168_empty_mileage_is_refused_as_invalid`, `Edge\ReturnMileageParsingTest::bh168_empty_value_compares_null_below_departure`, `Diff\ReservationReturnDiffTest::bh168_empty_value_same_as_reference` | http, edge, diff | `http/reservation-return/bh168.html` | ✔ | non |
| 2026-11-17 | BH-169 | `Http\ReservationReturnTest::bh169_empty_mileage_with_zero_departure_is_a_server_error`, `Edge\ReturnMileageParsingTest::bh169_empty_value_with_zero_departure_fails_without_writing`, `Diff\ReservationReturnDiffTest::bh169_server_error_same_as_reference` | http, edge, diff | — (500, corps non contractuel : proposition § 3.5) | ✔ | non |
| 2026-11-17 | BH-170 | `Http\ReservationReturnTest::bh170_negative_mileage_is_refused` | http | — | ✔ | non |
| 2026-11-17 | BH-171 | `Http\ReservationReturnTest::bh171_missing_or_wrong_csrf_token_is_refused`, `Diff\ReservationReturnDiffTest::bh171_csrf_same_as_reference` | http, diff | `http/reservation-return/bh171-missing.html` | ✔ | non |
| 2026-11-17 | BH-172 | `Http\ReservationReturnTest::bh172_extra_field_is_refused_without_assignment`, `Diff\ReservationReturnDiffTest::bh172_extra_field_same_as_reference` | http, diff | `http/reservation-return/bh172.html` | ✔ | non |
| 2026-11-17 | BH-173 | `Http\ReservationReturnTest::bh173_post_without_body_redisplays_form_without_error`, `Edge\ReturnMileageParsingTest::bh173_post_without_body_never_fails`, `Diff\ReservationReturnDiffTest::bh173_post_without_body_same_as_reference` | http, edge, diff | `http/reservation-return/bh173.html` | ✔ | non |
| 2026-11-17 | BH-174 | `Http\ReservationReturnTest::bh174_same_post_twice_closes_once` | http | — | ✔ | non |
| 2026-11-17 | BH-175 | `Http\ReservationReturnTest::bh175_form_of_finished_booking_is_displayed` | http | `http/reservation-return/bh175.html` | ✔ | non |
| 2026-11-17 | BH-176 | `Http\ReservationReturnTest::bh176_unknown_or_invalid_booking_is_not_found`, `Diff\ReservationReturnDiffTest::bh176_unknown_booking_same_as_reference` | http, diff | — (404, proposition § 2.5) | ✔ | non |
| 2026-11-17 | BH-177 | `Http\ReservationReturnTest::bh177_put_is_method_not_allowed` | http | — (405) | ✔ | non |
| 2026-11-17 | BH-178 | `Http\ReservationReturnTest::bh178_finished_booking_without_end_km_is_already_closed` | http | `http/reservation-return/bh178-anomalies.html` | ✔ | non |
| 2026-11-17 | BH-179 | `Http\ReservationReturnTest::bh179_returned_booking_moves_to_history_on_vehicle_page`, `Diff\ReservationReturnDiffTest::bh179_vehicle_page_after_return_same_as_reference` | http, diff | `http/reservation-return/bh179-vehicle-page.html` | ✔ | oui (historique exact, date de début) |
| 2026-11-17 | BH-100…179 | Propositions § 3.5 appliquées à titre provisoire, **en attente de décision** : BH-122 / BH-164 / BH-165 / BH-167 figés ; BH-169 figé en « 500 + base inchangée », corps non contractuel ; BH-133 / BH-173 figés (200 sans erreur) ; messages anglais et absence de bouton contractuels (goldens) ; exceptions non journalisées hors contrat. Corps 404/405 non contractuels et A5 comparé en diff uniquement (§ 2.5). Nouvelles classes support : `Support\DbDiff` (diff exact typé), `Support\FormJourney`, `Support\FormTestCase`, `Support\FormDiffTestCase` (aucune classe existante modifiée) | — | — | — | — |
| 2026-11-17 | BH-200 | `Http\ApiVehiclesTest::bh200_empty_fleet_returns_empty_array`, `Diff\ApiVehiclesDiffTest::bh200_empty_array_same_as_reference` | http, diff | `http/api-vehicles/empty.json` (+ `.sha256` `4f53cda18c2b…`) | ✔ | non |
| 2026-11-17 | BH-201 | `Http\ApiVehiclesTest::bh201_demo_fleet_byte_for_byte`, `Diff\ApiVehiclesDiffTest::bh201_demo_bytes_same_as_reference` | http, diff | `http/api-vehicles/demo.json` (+ `.sha256` `6fe447542af8c8e1bed02959c512fd78283e9ee89ebcdc1b71d4161953ccb556`) | ✔ | non |
| 2026-11-17 | BH-202 | `Http\ApiVehiclesTest::bh202_json_headers_without_session_cors_or_validators`, `Diff\ApiVehiclesDiffTest::bh202_headers_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-203 | `Http\ApiVehiclesTest::bh203_compact_body_without_bom_or_trailing_newline`, `Diff\ApiVehiclesDiffTest::bh203_body_bytes_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-204 | `Http\ApiVehiclesTest::bh204_ids_and_mileage_are_numbers_other_fields_strings`, `Edge\JsonEncodingTest::bh204_integer_columns_are_json_numbers`, `Diff\ApiVehiclesDiffTest::bh204_json_types_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-205 | `Http\ApiVehiclesTest::bh205_raw_plates_in_binary_order`, `Diff\ApiVehiclesDiffTest::bh205_raw_plate_order_same_as_reference` | http, diff | `http/api-vehicles/edge.json` | ✔ | oui (ordre complet des 27 plaques) |
| 2026-11-17 | BH-206 | `Http\ApiVehiclesTest::bh206_html_quotes_and_non_ascii_are_escaped`, `Edge\JsonEncodingTest::bh206_hex_escapes_for_html_quotes_slashes_and_non_ascii`, `Diff\ApiVehiclesDiffTest::bh206_escaping_same_as_reference` | http, edge, diff | `http/api-vehicles/edge.json` (+ `.sha256` `6f2bebf0956404a0bd189bfad551eb43a69ac8189d4c135596853141946a2a49`) | ✔ | oui (séquences d'échappement rétablies dans la fiche, le § 4.1 et l'exemple § 4.3 : elles avaient été décodées par l'outil d'écriture) |
| 2026-11-17 | BH-207 | `Http\ApiVehiclesTest::bh207_int32_max_mileage_is_a_plain_number`, `Edge\JsonEncodingTest::bh207_large_integers_without_exponent`, `Diff\ApiVehiclesDiffTest::bh207_large_numbers_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-208 | `Http\ApiVehiclesTest::bh208_label_of_maximum_length` | http | — | ✔ | non |
| 2026-11-17 | BH-209 | `Http\ApiVehiclesTest::bh209_overdue_booking_reported_as_next` | http | — | ✔ | non |
| 2026-11-17 | BH-210 | `Http\ApiVehiclesTest::bh210_same_start_bookings_give_that_start`, `Diff\ApiVehiclesDiffTest::bh210_same_start_order_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-211 | `Http\ApiVehiclesTest::bh211_unavailable_vehicles_still_report_next_booking` | http | — | ✔ | non |
| 2026-11-17 | BH-212 | `Http\ApiVehiclesTest::bh212_first_confirmed_of_a_long_list` | http | — | ✔ | non |
| 2026-11-17 | BH-213 | `Http\ApiVehiclesTest::bh213_naive_time_on_dst_day`, `Diff\ApiVehiclesDiffTest::bh213_dst_time_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-214 | `Http\ApiVehiclesTest::bh214_next_reservation_on_booking_scenario` | http | `http/api-vehicles/booking.json` (+ `.sha256` `8dff97bd1a28…`, 978 octets) | ✔ | non |
| 2026-11-17 | BH-215 | `Http\ApiVehiclesTest::bh215_inconsistent_data_unknown_status_is_null`, `Edge\JsonEncodingTest::bh215_unknown_status_is_json_null_never_empty_string`, `Diff\ApiVehiclesDiffTest::bh215_anomalies_and_php_log_same_as_reference` | http, edge, diff | `http/api-vehicles/anomalies.json` (+ `.sha256` `d6ceb3100b5f…`, 885 octets) | ✔ | oui (signatures A5 exactes) |
| 2026-11-17 | BH-216 | `Http\ApiVehiclesTest::bh216_realistic_fleet_byte_for_byte`, `Diff\ApiVehiclesDiffTest::bh216_nominal_bytes_same_as_reference` | http, diff | `http/api-vehicles/nominal.json` (+ `.sha256` `1d3add568c40fc1b27c55be698bc74d4c75e560387c88cf42adc9be769bc913a`) | ✔ | oui (empreinte) |
| 2026-11-17 | BH-217 | `Http\ApiVehiclesTest::bh217_volume_returns_all_vehicles`, `Diff\ApiVehiclesDiffTest::bh217_volume_bytes_and_performance_vs_reference` | http, diff | `http/api-vehicles/volume.json` (+ `.sha256` `98f20c5e1ee3…`, 58 504 octets) | ✔ | non (durée absolue non assertée : seule la comparaison ≤ 2 × app-ref l'est, médiane de 5 mesures) |
| 2026-11-17 | BH-218 | `Http\ApiVehiclesTest::bh218_query_parameters_are_ignored` | http | `http/api-vehicles/demo.json` | ✔ | non |
| 2026-11-17 | BH-219 | `Http\ApiVehiclesTest::bh219_no_content_negotiation_and_no_cors`, `Diff\ApiVehiclesDiffTest::bh219_accept_and_origin_same_as_reference` | http, diff | `http/api-vehicles/demo.json` | ✔ | non (deux requêtes distinctes, un en-tête chacune) |
| 2026-11-17 | BH-220 | `Http\ApiVehiclesTest::bh220_head_returns_json_headers_without_body` | http | — | ✔ | non |
| 2026-11-17 | BH-221 | `Http\ApiVehiclesTest::bh221_post_and_options_are_method_not_allowed_in_html`, `Diff\ApiVehiclesDiffTest::bh221_method_not_allowed_same_as_reference` | http, diff | — (405, corps non contractuel) | ✔ | oui (en-têtes, pas de `Set-Cookie`) |
| 2026-11-17 | BH-222 | `Http\ApiVehiclesTest::bh222_no_other_api_route`, `Diff\ApiVehiclesDiffTest::bh222_unknown_api_paths_same_as_reference` | http, diff | — (404, corps non contractuel) | ✔ | oui (en-têtes, pas de `Set-Cookie`) |
| 2026-11-17 | BH-223 | `Http\ApiVehiclesTest::bh223_wrong_credentials_block_the_public_api`, `Diff\ApiVehiclesDiffTest::bh223_wrong_credentials_same_as_reference` | http, diff | — (401, corps non contractuel) | ✔ | oui (HTML, pas de `Set-Cookie`) |
| 2026-11-17 | BH-224 | `Http\ApiVehiclesTest::bh224_valid_credentials_open_a_session`, `Diff\ApiVehiclesDiffTest::bh224_authenticated_session_cookie_same_as_reference` | http, diff | `http/api-vehicles/demo.json` | ✔ | non |
| 2026-11-17 | BH-200…224 | Propositions § 4.4 appliquées à titre provisoire, **en attente de décision** : octets exacts (golden `.json` sans normalisation + `.json.sha256`), BH-209 / BH-215 / BH-223 / BH-224 figés, seuil BH-217 ≤ 2 × référence, corps 401/404/405 non contractuels ; A5 comparé en diff uniquement (§ 2.5). Support : nouvelle classe `Support\ApiJson` (structure, golden + empreinte, `lit()`) ; `Support\HttpClient` modifié sans effet sur l'existant (un `Accept` fourni par l'appelant remplace celui par défaut au lieu de s'y ajouter). Suite complète verte 3 fois sur app-ref (252 tests). Fichier sonde `tests/src/Edge/ZzProbeTest.php` laissé par le diagnostic : **à supprimer** par la session principale | — | — | — | — |
| 2026-11-17 | BH-300 | `Http\AdminVehicleNewTest::bh300_form_without_credentials_is_unauthorized`, `Diff\AdminVehicleNewDiffTest::bh300_no_credentials_same_as_reference` | http, diff | — (401, corps vide) | ✔ | oui (corps vide, en-têtes communs) |
| 2026-11-17 | BH-301 | `Http\AdminVehicleNewTest::bh301_wrong_unknown_or_empty_credentials_are_unauthorized`, `Diff\AdminVehicleNewDiffTest::bh301_bad_credentials_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-302 | `Http\AdminVehicleNewTest::bh302_username_is_case_insensitive`, `Diff\AdminVehicleNewDiffTest::bh302_case_insensitive_username_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-303 | `Http\AdminVehicleNewTest::bh303_form_with_defaults_admin_link_and_no_button`, `Diff\AdminVehicleNewDiffTest::bh303_form_same_as_reference` | http, diff | `http/admin-vehicle-new/bh303.html` | ✔ | non |
| 2026-11-17 | BH-304 | `Http\AdminVehicleNewTest::bh304_nominal_creation_redirects_with_flash`, `Diff\AdminVehicleNewDiffTest::bh304_nominal_creation_same_as_reference` | http, diff | `http/admin-vehicle-new/bh304-after-redirect.html` | ✔ | non |
| 2026-11-17 | BH-305 | `Http\AdminVehicleNewTest::bh305_first_vehicle_on_empty_base_gets_id_1` | http | — | ✔ | non |
| 2026-11-17 | BH-306 | `Http\AdminVehicleNewTest::bh306_plate_is_normalized_before_storage`, `Edge\AdminInputParsingTest::bh306_plate_variants_are_normalized_to_belgian_format`, `Diff\AdminVehicleNewDiffTest::bh306_plate_normalization_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-307 | `Http\AdminVehicleNewTest::bh307_invalid_plate_is_refused_quoting_raw_input`, `Diff\AdminVehicleNewDiffTest::bh307_invalid_plate_message_same_as_reference` | http, diff | `http/admin-vehicle-new/bh307-{abc-123,1-ab-123,x-xyz-789}.html` | ✔ | oui (erreur de champ, saisie réaffichée) |
| 2026-11-17 | BH-308 | `Http\AdminVehicleNewTest::bh308_blank_plate_is_refused_after_trim`, `Diff\AdminVehicleNewDiffTest::bh308_blank_plate_same_as_reference` | http, diff | `http/admin-vehicle-new/bh308-{empty,spaces}.html` | ✔ | non |
| 2026-11-17 | BH-309 | `Http\AdminVehicleNewTest::bh309_existing_plate_is_a_server_error`, `Diff\AdminVehicleNewDiffTest::bh309_duplicate_plate_same_as_reference` | http, diff | — (500, corps non contractuel : proposition § 5.4) | ✔ | non (exemple § 5.3 aligné : suites, noms des tests) |
| 2026-11-17 | BH-310 | `Http\AdminVehicleNewTest::bh310_same_post_twice_gives_302_then_500_and_a_single_row`, `Diff\AdminVehicleNewDiffTest::bh310_double_post_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-311 | `Http\AdminVehicleNewTest::bh311_blank_brand_and_model_are_refused` | http | `http/admin-vehicle-new/bh311.html` | ✔ | non |
| 2026-11-17 | BH-312 | `Http\AdminVehicleNewTest::bh312_brand_and_model_are_trimmed`, `Diff\AdminVehicleNewDiffTest::bh312_trimmed_brand_and_model_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-313 | `Http\AdminVehicleNewTest::bh313_brand_and_model_longer_than_columns_are_stored_in_full`, `Edge\AdminInputParsingTest::bh313_brand_and_model_beyond_varchar_50_are_not_truncated`, `Diff\AdminVehicleNewDiffTest::bh313_overlong_brand_and_model_same_as_reference` | http, edge, diff | — | ✔ | non (variante multi-octets `é` ×51 en edge et diff) |
| 2026-11-17 | BH-314 | `Http\AdminVehicleNewTest::bh314_html_and_unicode_stored_raw_and_displayed_escaped`, `Diff\AdminVehicleNewDiffTest::bh314_html_and_unicode_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-315 | `Http\AdminVehicleNewTest::bh315_negative_mileage_is_refused` | http | `http/admin-vehicle-new/bh315.html` | ✔ | non |
| 2026-11-17 | BH-316 | `Http\AdminVehicleNewTest::bh316_zero_mileage_is_accepted` | http | — | ✔ | non |
| 2026-11-17 | BH-317 | `Http\AdminVehicleNewTest::bh317_non_numeric_or_huge_mileage_is_not_valid`, `Diff\AdminVehicleNewDiffTest::bh317_invalid_mileage_same_as_reference` | http, diff | `http/admin-vehicle-new/bh317-{abc,huge}.html` | ✔ | non |
| 2026-11-17 | BH-318 | `Http\AdminVehicleNewTest::bh318_decimal_mileage_is_truncated`, `Edge\AdminInputParsingTest::bh318_decimal_comma_is_truncated_down`, `Diff\AdminVehicleNewDiffTest::bh318_decimal_mileage_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-319 | `Http\AdminVehicleNewTest::bh319_exponent_mileage_is_stored_as_thousand`, `Edge\AdminInputParsingTest::bh319_exponent_is_read_as_thousand`, `Diff\AdminVehicleNewDiffTest::bh319_exponent_mileage_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-320 | `Http\AdminVehicleNewTest::bh320_mileage_of_two_pow_31_is_stored`, `Edge\AdminInputParsingTest::bh320_two_pow_31_is_stored_as_64_bit_integer`, `Diff\AdminVehicleNewDiffTest::bh320_int32_overflow_mileage_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-321 | `Http\AdminVehicleNewTest::bh321_empty_mileage_is_a_server_error`, `Edge\AdminInputParsingTest::bh321_empty_mileage_fails_without_writing`, `Diff\AdminVehicleNewDiffTest::bh321_empty_mileage_same_as_reference` | http, edge, diff | — (500) | ✔ | non |
| 2026-11-17 | BH-322 | `Http\AdminVehicleNewTest::bh322_status_two_is_stored` | http | — | ✔ | non |
| 2026-11-17 | BH-323 | `Http\AdminVehicleNewTest::bh323_unknown_status_is_not_valid`, `Diff\AdminVehicleNewDiffTest::bh323_unknown_status_same_as_reference` | http, diff | `http/admin-vehicle-new/bh323-{3,abc}.html` | ✔ | oui (erreur dans le `<div>` du `<select>`) |
| 2026-11-17 | BH-324 | `Http\AdminVehicleNewTest::bh324_missing_status_is_a_server_error`, `Edge\AdminInputParsingTest::bh324_missing_status_fails_without_writing`, `Diff\AdminVehicleNewDiffTest::bh324_missing_status_same_as_reference` | http, edge, diff | — (500) | ✔ | non |
| 2026-11-17 | BH-325 | `Http\AdminVehicleNewTest::bh325_missing_or_wrong_csrf_token_is_refused`, `Diff\AdminVehicleNewDiffTest::bh325_csrf_same_as_reference` | http, diff | `http/admin-vehicle-new/bh325-missing.html` | ✔ | non |
| 2026-11-17 | BH-326 | `Http\AdminVehicleNewTest::bh326_extra_fields_are_refused_without_assignment`, `Diff\AdminVehicleNewDiffTest::bh326_extra_fields_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-327 | `Http\AdminVehicleNewTest::bh327_post_without_body_redisplays_form_without_error`, `Diff\AdminVehicleNewDiffTest::bh327_post_without_body_same_as_reference` | http, diff | `http/admin-vehicle-new/bh327.html` | ✔ | non |
| 2026-11-17 | BH-328 | `Http\AdminVehicleNewTest::bh328_put_is_method_not_allowed` | http | — (405) | ✔ | non |
| 2026-11-17 | BH-329 | `Http\AdminVehicleNewTest::bh329_post_without_credentials_nor_session_is_unauthorized`, `Diff\AdminVehicleNewDiffTest::bh329_post_without_credentials_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-330 | `Http\AdminVehicleNewTest::bh330_flash_shows_account_name_not_typed_name`, `Diff\AdminVehicleNewDiffTest::bh330_uppercase_username_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-331 | `Http\AdminVehicleNewTest::bh331_created_vehicle_page`, `Diff\AdminVehicleNewDiffTest::bh331_vehicle_page_after_creation_same_as_reference` | http, diff | `http/admin-vehicle-new/bh331-vehicle-page.html` | ✔ | oui (message « Aucune réservation à venir. » dans un `<li>`, historique vide) |
| 2026-11-17 | BH-332 | `Http\AdminVehicleNewTest::bh332_created_vehicle_in_list_and_api_in_plate_order`, `Diff\AdminVehicleNewDiffTest::bh332_list_and_api_after_creation_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-333 | `Http\AdminVehicleNewTest::bh333_post_without_authorization_in_same_session_is_accepted`, `Edge\AdminInputParsingTest::bh333_basic_authentication_is_remembered_by_the_session`, `Diff\AdminVehicleNewDiffTest::bh333_session_remembers_authentication_same_as_reference` | http, edge, diff | — | ✔ | non |
| 2026-11-17 | BH-334 | `Http\AdminVehicleNewTest::bh334_session_keeps_admin_link_on_public_list`, `Diff\AdminVehicleNewDiffTest::bh334_admin_link_from_session_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-335 | `Http\AdminVehicleNewTest::bh335_created_at_is_set_from_the_container_clock`, `Diff\AdminVehicleNewDiffTest::bh335_created_at_same_date_as_reference` | http, diff | — | ✔ | oui (type TEXT) |
| 2026-11-17 | BH-336 | `Http\AdminVehicleNewTest::bh336_refused_request_still_opens_a_session`, `Diff\AdminVehicleNewDiffTest::bh336_session_cookie_on_401_same_as_reference` | http, diff | — | ✔ | non |
| 2026-11-17 | BH-337 | `Http\AdminVehicleNewTest::bh337_successful_creation_sets_session_cookie`, `Diff\AdminVehicleNewDiffTest::bh337_session_cookies_of_a_success_same_as_reference` | http, diff | — | ✔ | oui (cookie posé au GET authentifié, pas au POST) |
| 2026-11-17 | BH-300…337 | Propositions § 5.4 appliquées à titre provisoire, **en attente de décision** : BH-309 / BH-310 / BH-321 / BH-324 figés en « 500 + base inchangée », corps non contractuel ; BH-302 / BH-330, BH-333 / BH-334, BH-313, BH-318 / BH-319 figés ; mot de passe du compte de test lu dans `FLEET_ADMIN_PASSWORD` (repli : valeur step-00, uniquement dans `tests/`). A4 : diff exact, `created_at` vérifié au format `Y-m-d H:i:s` et réduit à sa date `2026-01-15` (heure jamais assertée) ; en suite diff, seul le `created_at` des véhicules **ajoutés** est réduit à sa date. Corps 401/405 non contractuels et A5 comparé en diff uniquement (§ 2.5). Support : nouveau trait `Support\AdminVehicleForm` ; `Support\HttpClient::withoutBasicAuth()` ajouté ; `Support\FormDiffTestCase::comparableDbLine()` ajouté (identité par défaut : aucun effet sur l'existant). Suite complète verte 3 fois sur app-ref (329 tests). Le fichier sonde `tests/src/Edge/ZzProbeTest.php` signalé au journal BH-200…224 n'existe plus (251 tests avant cette zone) | — | — | — | — |
| 2026-11-17 | BH-400 | `Cli\FleetReportTest::bh400_readme_example_for_may_2016`, `Diff\FleetReportDiffTest::bh400_readme_report_same_as_reference` | cli, diff | `cli/report/demo-2016-05.txt` | ✔ | non |
| 2026-11-17 | BH-401 | `Cli\FleetReportTest::bh401_january_report_table_with_total`, `Diff\FleetReportDiffTest::bh401_table_rendering_same_as_reference` | cli, diff | `cli/report/2026-01.txt` | ✔ | non |
| 2026-11-17 | BH-402 | `Cli\FleetReportTest::bh402_default_month_follows_container_clock`, `Diff\FleetReportDiffTest::bh402_default_month_same_as_reference` | cli, diff | `cli/report/2026-01.txt` | ✔ | non (comparé à la sortie explicite `--month=2026-01`) |
| 2026-11-17 | BH-403 | `Cli\FleetReportTest::bh403_short_attached_and_spaced_option_syntaxes_are_equivalent`, `Diff\FleetReportDiffTest::bh403_option_syntaxes_same_as_reference` | cli, diff | `cli/report/2025-11.txt` | ✔ | non |
| 2026-11-17 | BH-404 | `Cli\FleetReportTest::bh404_ties_at_300_km_keep_observed_order`, `Edge\ReportOrderingTest::bh404_three_ties_at_300_km`, `Diff\FleetReportDiffTest::bh404_tie_order_at_300_km_same_as_reference` | cli, edge, diff | `cli/report/2025-11.txt` | ✔ | non (exemple § 6.4 : ligne « Tests » ajoutée) |
| 2026-11-17 | BH-405 | `Cli\FleetReportTest::bh405_ties_at_250_km_keep_observed_order`, `Edge\ReportOrderingTest::bh405_three_ties_at_250_km`, `Diff\FleetReportDiffTest::bh405_tie_order_at_250_km_same_as_reference` | cli, edge, diff | `cli/report/2026-01.txt` | ✔ | non |
| 2026-11-17 | BH-406 | `Cli\FleetReportTest::bh406_four_ties_at_100_km_on_edge_data`, `Edge\ReportOrderingTest::bh406_four_ties_at_100_km`, `Diff\FleetReportDiffTest::bh406_tie_order_on_edge_data_same_as_reference` | cli, edge, diff | `cli/report/edge-2026-01.txt` | ✔ | non |
| 2026-11-17 | BH-407 | `Cli\FleetReportTest::bh407_month_without_data_gives_header_and_zero_total`, `Diff\FleetReportDiffTest::bh407_empty_table_same_as_reference` | cli, diff | `cli/report/2025-12.txt` | ✔ | non |
| 2026-11-17 | BH-408 | `Cli\FleetReportTest::bh408_month_bounds_on_end_date`, `Diff\FleetReportDiffTest::bh408_month_bounds_same_as_reference` | cli, diff | `cli/report/2026-01.txt` | ✔ | oui (réservation exclue = `1-REP-008`, 101 km) |
| 2026-11-17 | BH-409 | `Cli\FleetReportTest::bh409_february_with_very_large_numbers`, `Diff\FleetReportDiffTest::bh409_large_numbers_same_as_reference` | cli, diff | `cli/report/2026-02.txt` | ✔ | non |
| 2026-11-17 | BH-410 | `Cli\FleetReportTest::bh410_bookings_around_dst_changes_are_counted`, `Diff\FleetReportDiffTest::bh410_dst_months_same_as_reference` | cli, diff | `cli/report/2025-10.txt`, `cli/report/2026-03.txt` | ✔ | oui (lignes et totaux observés) |
| 2026-11-17 | BH-411 | `Cli\FleetReportTest::bh411_leap_day_counted_by_end_date` | cli | `cli/report/2024-02.txt`, `cli/report/2024-03.txt` | ✔ | oui (lignes observées) |
| 2026-11-17 | BH-412 | `Cli\FleetReportTest::bh412_month_13_is_accepted_with_next_january_data`, `Edge\ReportMonthOverflowTest::bh412_month_13_reports_next_january`, `Diff\FleetReportDiffTest::bh412_month_13_same_as_reference` | cli, edge, diff | `cli/report/2025-13.txt` | ✔ | non |
| 2026-11-17 | BH-413 | `Cli\FleetReportTest::bh413_out_of_range_months_are_accepted`, `Edge\ReportMonthOverflowTest::bh413_month_00_and_13_overflow_to_neighbour_months`, `Diff\FleetReportDiffTest::bh413_out_of_range_months_same_as_reference` | cli, edge, diff | `cli/report/{2026-00,2016-13,0000-01,9999-12}.txt` | ✔ | oui (`2016-13` = janvier 2017) |
| 2026-11-17 | BH-414 | `Cli\FleetReportTest::bh414_invalid_month_format_message_on_stdout_with_code_1`, `Diff\FleetReportDiffTest::bh414_invalid_formats_same_as_reference` | cli, diff | — (stdout exact asserté : une ligne + `\n`) | ✔ | non |
| 2026-11-17 | BH-415 | `Cli\FleetReportTest::bh415_empty_month_option_requires_a_value`, `Diff\FleetReportDiffTest::bh415_missing_option_value_same_as_reference` | cli, diff | — (message de stderr seul contractuel) | ✔ | non |
| 2026-11-17 | BH-416 | `Cli\FleetReportTest::bh416_non_numeric_cost_aborts_in_dev`, `Edge\ReportInconsistentDataTest::bh416_dev_aborts_before_any_output`, `Diff\FleetReportDiffTest::bh416_dev_failure_on_inconsistent_data_same_as_reference` | cli, edge, diff | — | ✔ | oui (A5 : aucune entrée) |
| 2026-11-17 | BH-417 | `Cli\FleetReportTest::bh417_non_numeric_cost_ignored_in_prod`, `Edge\ReportInconsistentDataTest::bh417_prod_counts_non_numeric_cost_as_zero_and_negative_km`, `Diff\FleetReportDiffTest::bh417_prod_report_on_inconsistent_data_same_as_reference` | cli, edge, diff | `cli/report/anomalies-2026-01-prod.txt` | ✔ | oui (A5 : un `PHP Warning: A non-numeric value encountered`) |
| 2026-11-17 | BH-418 | `Cli\FleetReportTest::bh418_unknown_option_is_refused`, `Diff\FleetReportDiffTest::bh418_unknown_option_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-419 | `Cli\FleetReportTest::bh419_positional_month_is_refused`, `Diff\FleetReportDiffTest::bh419_positional_argument_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-420 | `Cli\FleetReportTest::bh420_quiet_prints_nothing` | cli | — | ✔ | non |
| 2026-11-17 | BH-421 | `Cli\FleetReportTest::bh421_prod_environment_gives_same_report_on_sane_data`, `Diff\FleetReportDiffTest::bh421_prod_environment_same_as_reference` | cli, diff | `cli/report/2026-01.txt` | ✔ | non |
| 2026-11-17 | BH-422 | `Cli\FleetReportTest::bh422_ansi_colours_title_and_table_headers`, `Diff\FleetReportDiffTest::bh422_ansi_output_same_as_reference` | cli, diff | `cli/report/2026-01-ansi.txt` | ✔ | **oui** (en-têtes du tableau colorés aussi, pas seulement le titre) |
| 2026-11-17 | BH-423 | `Cli\FleetReportTest::bh423_volume_report_content`, `Diff\FleetReportDiffTest::bh423_volume_report_content_and_performance_vs_reference` | cli, diff | `cli/report/volume-2024-03.txt` | ✔ | non (durée : médiane de 3 exécutions, app ≤ 2 × app-ref, jamais de durée absolue) |
| 2026-11-17 | BH-424 | `Cli\FleetReportTest::bh424_report_never_writes_to_the_database` | cli | — | ✔ | non (6 variantes, empreinte vérifiée après chacune ; base aussi vérifiée dans chaque test de la zone) |
| 2026-11-17 | BH-425 | `Cli\FleetReportTest::bh425_list_and_help_describe_the_report`, `Diff\FleetReportDiffTest::bh425_list_and_help_same_as_reference` | cli, diff | `cli/help/list.txt`, `cli/help/fleet-report.txt` | ✔ | non (défaut de `--month` remplacé par `<mois courant>` dans le golden, `2026-01` asserté à part) |
| 2026-11-17 | BH-450 | `Cli\FleetDemoLoadTest::bh450_loads_demo_data_into_empty_database`, `Diff\FleetDemoLoadDiffTest::bh450_load_into_empty_database_same_as_reference` | cli, diff | — (stdout exact asserté) | ✔ | non |
| 2026-11-17 | BH-451 | `Cli\FleetDemoLoadTest::bh451_replaces_realistic_fleet_without_asking`, `Diff\FleetDemoLoadDiffTest::bh451_replace_realistic_fleet_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-452 | `Cli\FleetDemoLoadTest::bh452_inconsistent_data_removed_without_error`, `Diff\FleetDemoLoadDiffTest::bh452_replace_inconsistent_data_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-453 | `Cli\FleetDemoLoadTest::bh453_edge_data_removed_including_non_normalized_plates` | cli | — | ✔ | non |
| 2026-11-17 | BH-454 | `Cli\FleetDemoLoadTest::bh454_running_twice_is_idempotent` | cli | — | ✔ | non |
| 2026-11-17 | BH-455 | `Cli\FleetDemoLoadTest::bh455_non_interactive_flag_changes_nothing`, `Diff\FleetDemoLoadDiffTest::bh455_non_interactive_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-456 | `Cli\FleetDemoLoadTest::bh456_quiet_prints_nothing_but_still_loads` | cli | — | ✔ | non |
| 2026-11-17 | BH-457 | `Cli\FleetDemoLoadTest::bh457_runs_in_prod_environment_without_guard`, `Diff\FleetDemoLoadDiffTest::bh457_prod_environment_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-458 | `Cli\FleetDemoLoadTest::bh458_extra_argument_is_refused_without_touching_data`, `Diff\FleetDemoLoadDiffTest::bh458_extra_argument_same_as_reference` | cli, diff | — | ✔ | non |
| 2026-11-17 | BH-459 | `Cli\FleetDemoLoadTest::bh459_help_and_list_describe_without_accents`, `Diff\FleetDemoLoadDiffTest::bh459_help_and_list_same_as_reference` | cli, diff | `cli/help/fleet-demo-load.txt`, `cli/help/list.txt` | ✔ | non |
| 2026-11-17 | BH-460 | `Cli\FleetDemoLoadTest::bh460_after_loading_pages_api_and_report_match_demo_fiches`, `Diff\FleetDemoLoadDiffTest::bh460_pages_api_and_report_after_loading_same_as_reference` | cli, diff | lus sans réenregistrement : `http/vehicles-list/bh010.html`, `http/api-vehicles/demo.json`, `cli/report/demo-2016-05.txt` | ✔ | non |
| 2026-11-17 | BH-400…460 | Propositions § 6.5 appliquées à titre provisoire, **en attente de décision** : ordre des ex æquo figé (BH-404 à BH-406), `--month` en débordement préservé (BH-412, BH-413), message de format sur stdout + code 1 (BH-414), divergence dev / prod figée (BH-416, BH-417), `fleet:demo:load` destructive sans garde-fou figée (BH-450 à BH-457), défaut de `--month` normalisé dans le golden d'aide (BH-425), rendu des tableaux octet par octet (stdout brut, aucune normalisation ; aide et liste : `Normalizer::cli`). A2 : seul le message du bloc d'exception est comparé (classe et rappel de syntaxe non contractuels). A5 : journal vide asserté sur toute exécution réussie, sauf BH-417 (un warning). Exécution via `CONSOLE_<CIBLE>_CMD` (test **incomplet** si absente), sans terminal, stdout / stderr séparés. Support : nouvelles classes `Support\ConsoleRun`, `Support\ConsoleCommands` (trait), `Support\ConsoleTestCase`, `Support\ConsoleDiffTestCase` (aucune classe existante modifiée) ; `src/Diff/README.md` complété (normalisations console). Suite complète verte 3 fois sur app-ref (404 tests : http 172, cli 37, edge 49, diff 146). Aucun fichier de sonde | — | — | — | — |
| 2026-11-17 | BH-001…337, BH-460 | **`--fix` step-01, ACC-001** (gel levé par l'utilisateur) : registre unique `Support\AcceptedChanges` (`step`, `stepAtLeast`, `applies`, `appliesToComparison` ; étape servie lue dans `APP_STEP` / `REF_STEP`). A2 : `CaseTestCase::assertCommonHeaders` → `assertCacheHeaders` → `AcceptedChanges::assertCacheHeaders` (Cache-Control exact + présence / absence d'`Expires`, selon l'étape de la cible de la réponse et l'usage de la session) ; `Http\ApiVehiclesTest::assertJsonOk` idem (seule assertion littérale `no-cache` restante). Diff : `DiffTestCase::contractHeaders` (désormais partagé par `FormDiffTestCase`) et `Diff\ApiVehiclesDiffTest::headers` normalisent Cache-Control / Expires via `AcceptedChanges::comparableCacheHeaders` ; autres en-têtes, corps, base, A5 inchangés. `HttpResponse` porte la cible et l'envoi du cookie de session (`HttpClient`). Aucun golden modifié (aucun golden ne capture d'en-tête) | http, edge, diff | — | ✔ | oui (lignes BH-202, BH-221, BH-224, BH-300, exemple § 2.4, rappels § 2, § 3, § 5 : « ACC-001 à partir de step-01 ») |
| 2026-11-17 | BH-415, BH-416, BH-418, BH-419, BH-458 | **`--fix` step-01, famille E** : `Support\ConsoleRun::exceptionMessage` n'extrait plus que la ligne de message de l'encadré (titre `[Classe]` 2.8 et localisation `In <fichier> line <n>:` 3.4 ignorés) ; utilisé par `ConsoleCommands::assertConsoleError` (cli, edge) et `ConsoleDiffTestCase::assertConsoleSameAsReference` (diff) ; attentes inchangées | cli, edge, diff | — | ✔ | oui (BH-416 : habillage step-01 observé, § 6.1 A2) |
| 2026-11-17 | BH-425 | **`--fix` step-01, ACC-002** : `Cli\FleetReportTest::bh425_list_and_help_describe_the_report` vérifie à toutes les étapes la partie fleet:* (`AcceptedChanges::fleetCommands`, `commandHelpContract` via `ConsoleCommands::helpContract`) + défaut `2026-01` ; goldens complets consultés seulement si ACC-002 ne s'applique pas (step-00). `Diff\FleetReportDiffTest::bh425_list_and_help_same_as_reference` : partie fleet:* comparée dès qu'ACC-002 s'applique à un côté | cli, diff | `cli/help/list.txt`, `cli/help/fleet-report.txt` (step-00 uniquement, non modifiés) | ✔ | oui (« ACC-002 à partir de step-01 ») |
| 2026-11-17 | BH-459 | **`--fix` step-01, ACC-002** : `Cli\FleetDemoLoadTest::bh459_help_and_list_describe_without_accents` et `Diff\FleetDemoLoadDiffTest::bh459_help_and_list_same_as_reference`, même mécanisme que BH-425 (synopsis `fleet:demo:load`, aucune option propre, description sans accents) | cli, diff | `cli/help/fleet-demo-load.txt`, `cli/help/list.txt` (step-00 uniquement, non modifiés) | ✔ | oui (« ACC-002 à partir de step-01 ») |

## Blocages

Fiches mises en attente (décision, environnement…), une par ligne : `- BH-NNN : ⏸ raison`.
