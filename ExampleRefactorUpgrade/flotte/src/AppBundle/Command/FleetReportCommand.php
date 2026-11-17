<?php

namespace AppBundle\Command;

use Symfony\Bundle\FrameworkBundle\Command\ContainerAwareCommand;
use Symfony\Component\Console\Helper\Table;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;

class FleetReportCommand extends ContainerAwareCommand
{
    protected function configure()
    {
        $this
            ->setName('fleet:report')
            ->setDescription('Rapport mensuel d\'utilisation de la flotte')
            ->addOption('month', 'm', InputOption::VALUE_REQUIRED, 'Mois au format AAAA-MM', date('Y-m'));
    }

    protected function execute(InputInterface $input, OutputInterface $output)
    {
        $month = $input->getOption('month');
        if (!preg_match('/^\d{4}-\d{2}$/', $month)) {
            $output->writeln('<error>Format de mois invalide (attendu : AAAA-MM).</error>');

            return 1;
        }

        $rows = $this->getContainer()->get('app.reservation_manager')->monthlyReport($month);

        $output->writeln(sprintf('<info>Rapport de flotte - %s</info>', $month));

        $table = new Table($output);
        $table->setHeaders(array('Plaque', 'Réservations', 'Km', 'Coût'));

        $totalKm = 0;
        $totalCost = 0;
        foreach ($rows as $row) {
            $table->addRow(array(
                $row['plate'],
                $row['count'],
                number_format($row['km'], 0, ',', '.'),
                number_format($row['cost'], 2, ',', ' ').' €',
            ));
            $totalKm += $row['km'];
            $totalCost += $row['cost'];
        }
        $table->addRow(array('TOTAL', '', number_format($totalKm, 0, ',', '.'), number_format($totalCost, 2, ',', ' ').' €'));
        $table->render();

        return 0;
    }
}
