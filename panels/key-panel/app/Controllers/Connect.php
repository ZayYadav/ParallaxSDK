<?php

namespace App\Controllers;

use App\Models\KeysModel;
use CodeIgniter\Database\BaseConnection;
use CodeIgniter\I18n\Time;

class Connect extends BaseController
{
    private KeysModel $model;
    private BaseConnection $database;
    private string $tokenSecret;

    public function __construct()
    {
        $this->model = new KeysModel();
        $this->database = db_connect();
        $this->tokenSecret = trim((string) env('ONECORE_LEGACY_TOKEN_SECRET', ''));
    }

    public function index()
    {
        if (strtolower($this->request->getMethod()) === 'post') {
            return $this->indexPost();
        }

        return view('Public/connect', [
            'title' => 'Get an activation key',
            'isSignedIn' => session()->has('userid'),
        ]);
    }

    private function indexPost()
    {
        if (strlen($this->tokenSecret) < 32) {
            log_message('error', 'ONECORE_LEGACY_TOKEN_SECRET is missing or too short.');
            return $this->jsonError('SERVER CONFIGURATION ERROR');
        }

        $game = trim((string) $this->request->getPost('game'));
        $userKey = trim((string) $this->request->getPost('user_key'));
        $serial = trim((string) $this->request->getPost('serial'));

        if ($game === '' || $userKey === '' || $serial === ''
            || strlen($game) > 64 || strlen($userKey) > 128 || strlen($serial) > 256) {
            return $this->jsonError('INVALID PARAMETER');
        }

        $maintenance = $this->database->table('onoff')->where('id', 1)->get()->getRowArray();
        if (($maintenance['status'] ?? 'off') === 'on') {
            return $this->response->setJSON([
                'status' => true,
                'reason' => (string) ($maintenance['myinput'] ?? 'Maintenance in progress'),
            ]);
        }

        $this->database->transStart();
        $key = $this->model->getKeysGame(['user_key' => $userKey, 'game' => $game]);
        if (!$key) {
            $this->database->transRollback();
            return $this->jsonError('USER OR GAME NOT REGISTERED');
        }
        if ((int) $key->status !== 1) {
            $this->database->transRollback();
            return $this->jsonError('USER BLOCKED');
        }

        $now = Time::now('UTC');
        $expiry = $key->expired_date ? new Time($key->expired_date, 'UTC') : null;
        if ($expiry && !$now->isBefore($expiry)) {
            $this->database->transRollback();
            return $this->jsonError('EXPIRED KEY');
        }
        if (!$expiry) {
            $expiry = $now->addHours((int) $key->duration);
            $this->model->update($key->id_keys, [
                'expired_date' => $expiry->format('Y-m-d H:i:s'),
            ]);
        }

        $devices = array_values(array_filter(array_map(
            'trim',
            explode(',', (string) ($key->devices ?? ''))
        )));
        if (!in_array($serial, $devices, true)) {
            if (count($devices) >= (int) $key->max_devices) {
                $this->database->transRollback();
                return $this->jsonError('MAX DEVICE REACHED');
            }
            $devices[] = $serial;
            $this->model->update($key->id_keys, ['devices' => implode(',', $devices)]);
        }

        $server = $this->database->table('modname')->where('id', 1)->get()->getRowArray() ?? [];
        $copy = $this->database->table('_ftext')->where('id', 1)->get()->getRowArray() ?? [];
        $feature = $this->database->table('Feature')->where('id', 1)->get()->getRowArray() ?? [];
        $this->database->transComplete();
        if ($this->database->transStatus() === false) {
            return $this->jsonError('SERVER TRANSACTION FAILED');
        }

        $expiryString = $expiry->format('Y-m-d H:i:s');
        $real = "$game-$userKey-$serial-$this->tokenSecret";
        return $this->response
            ->setHeader('Cache-Control', 'no-store')
            ->setJSON([
                'status' => true,
                'data' => [
                    'real' => $real,
                    // Kept for backwards compatibility with existing legacy clients.
                    'token' => md5($real),
                    'modname' => (string) ($server['modname'] ?? ''),
                    'mod_status' => (string) ($copy['_status'] ?? ''),
                    'credit' => (string) ($copy['_ftext'] ?? ''),
                    'ESP' => (string) ($feature['ESP'] ?? 'off'),
                    'Item' => (string) ($feature['Item'] ?? 'off'),
                    'AIM' => (string) ($feature['AIM'] ?? 'off'),
                    'SilentAim' => (string) ($feature['SilentAim'] ?? 'off'),
                    'BulletTrack' => (string) ($feature['BulletTrack'] ?? 'off'),
                    'Floating' => (string) ($feature['Floating'] ?? 'off'),
                    'Memory' => (string) ($feature['Memory'] ?? 'off'),
                    'Setting' => (string) ($feature['Setting'] ?? 'off'),
                    'expired_date' => $expiryString,
                    'EXP' => $expiryString,
                    'exdate' => $expiryString,
                    'device' => (int) $key->max_devices,
                    'rng' => time(),
                ],
            ]);
    }

    private function jsonError(string $reason)
    {
        return $this->response
            ->setHeader('Cache-Control', 'no-store')
            ->setJSON(['status' => false, 'reason' => $reason]);
    }
}
