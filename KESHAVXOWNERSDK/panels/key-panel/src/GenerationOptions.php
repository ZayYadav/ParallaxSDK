<?php

declare(strict_types=1);

namespace ParallaxPanel;

use InvalidArgumentException;
use PDO;
use Throwable;

final class GenerationOptions
{
    public const GAME = 'game';
    public const DURATION = 'duration';

    /** @return array<string,string> */
    public static function games(PDO $db): array
    {
        return self::options($db, self::GAME);
    }

    /** @return array<string,string> */
    public static function durations(PDO $db): array
    {
        return self::options($db, self::DURATION);
    }

    /** @return array<string,string> */
    public static function options(PDO $db, string $type): array
    {
        self::assertType($type);
        $statement = $db->prepare(
            'SELECT option_value,option_label FROM key_generation_options WHERE option_type=? ORDER BY sort_order,id'
        );
        $statement->execute([$type]);
        $options = [];
        foreach ($statement->fetchAll() as $row) {
            $options[(string) $row['option_value']] = (string) $row['option_label'];
        }
        return $options;
    }

    /** @return array<string,mixed>|null */
    public static function findById(PDO $db, string $type, int $id): ?array
    {
        self::assertType($type);
        $statement = $db->prepare(
            'SELECT id,option_value,option_label FROM key_generation_options WHERE id=? AND option_type=? LIMIT 1'
        );
        $statement->execute([$id, $type]);
        $row = $statement->fetch();
        return is_array($row) ? $row : null;
    }

    public static function contains(PDO $db, string $type, string $value): bool
    {
        self::assertType($type);
        $statement = $db->prepare(
            'SELECT COUNT(*) FROM key_generation_options WHERE option_type=? AND option_value=?'
        );
        $statement->execute([$type, $value]);
        return (int) $statement->fetchColumn() === 1;
    }

    /**
     * @return list<array{value:string,label:string}>
     */
    public static function parse(string $type, string $text): array
    {
        self::assertType($type);
        $lines = preg_split('/\R/u', $text) ?: [];
        $parsed = [];
        foreach ($lines as $line) {
            $line = trim($line);
            if ($line === '') {
                continue;
            }
            [$rawValue, $rawLabel] = array_pad(explode('|', $line, 2), 2, '');
            $value = trim($rawValue);
            $label = trim($rawLabel);
            if ($type === self::GAME) {
                $value = strtoupper($value);
                if (preg_match('/^[A-Z0-9_-]{2,64}$/D', $value) !== 1) {
                    throw new InvalidArgumentException('Game IDs may contain only A-Z, 0-9, underscore and hyphen.');
                }
                $label = $label !== '' ? $label : $value;
            } else {
                if (preg_match('/^[1-9][0-9]{0,4}$/D', $value) !== 1 || (int) $value > 87600) {
                    throw new InvalidArgumentException('Duration must be an integer from 1 to 87600 hours.');
                }
                $value = (string) ((int) $value);
                $label = $label !== '' ? $label : $value . ' hours';
            }
            if (strlen($label) > 100 || preg_match('/[\x00-\x1F\x7F]/', $label) === 1) {
                throw new InvalidArgumentException('Option labels must be 100 characters or fewer.');
            }
            if (isset($parsed[$value])) {
                throw new InvalidArgumentException('Duplicate option value: ' . $value);
            }
            $parsed[$value] = ['value' => $value, 'label' => $label];
        }
        if ($parsed === [] || count($parsed) > 50) {
            throw new InvalidArgumentException('Each option list must contain between 1 and 50 entries.');
        }
        return array_values($parsed);
    }

    /**
     * @param list<array{value:string,label:string}> $games
     * @param list<array{value:string,label:string}> $durations
     */
    public static function replace(PDO $db, array $games, array $durations): void
    {
        $db->beginTransaction();
        try {
            $db->exec('DELETE FROM key_generation_options');
            $statement = $db->prepare(
                'INSERT INTO key_generation_options (option_type,option_value,option_label,sort_order) VALUES (?,?,?,?)'
            );
            foreach ([[self::GAME, $games], [self::DURATION, $durations]] as [$type, $options]) {
                foreach ($options as $index => $option) {
                    $statement->execute([$type, $option['value'], $option['label'], $index + 1]);
                }
            }
            $db->commit();
        } catch (Throwable $error) {
            if ($db->inTransaction()) {
                $db->rollBack();
            }
            throw $error;
        }
    }

    /** @param array<string,string> $options */
    public static function toEditorText(array $options): string
    {
        $lines = [];
        foreach ($options as $value => $label) {
            $lines[] = $value . '|' . $label;
        }
        return implode("\n", $lines);
    }

    private static function assertType(string $type): void
    {
        if (!in_array($type, [self::GAME, self::DURATION], true)) {
            throw new InvalidArgumentException('Unsupported generation option type.');
        }
    }
}
