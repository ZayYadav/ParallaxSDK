<?php

namespace App\Controllers;

use App\Models\HistoryModel;
use App\Models\KeysModel;
use App\Models\UserModel;
use Config\Services;

class Keys extends BaseController
{
    protected $userModel, $model, $user,$userId;

public function __construct()
{
    $this->userModel = new UserModel();
    $this->user = $this->userModel->getUser();
    $this->model = new KeysModel();
    $this->time = new \CodeIgniter\I18n\Time;

    $this->userId = session()->get('userid');

    /* ------- Load Games Config ------- */

   $configPath = FCPATH . 'ParallaxGames/games.json';
    if(file_exists($configPath)){

        $config = json_decode(file_get_contents($configPath), true);

        $this->game_list = $config['games'];
        $this->duration  = $config['duration'];
        $this->price     = $config['price'];

    }else{

        $this->game_list = [];
        $this->duration  = [];
        $this->price     = [];

    }
}

    public function index()
    {
        $model = $this->model;
        $user = $this->user;

        if ($user->level != 1) {
            $keys = $model->where('registrator', $user->username)
                ->orderBy('id_keys', 'DESC')
                ->findAll();
        } else {
            // The old query selected only user_key, leaving every other
            // license column blank in the admin view.
            $keys = $model->orderBy('id_keys', 'DESC')->findAll();
        }
        $data = [
            'title' => 'Keys',
            'user' => $user,
            'keylist' => $keys,
            'time' => $this->time,
        ];
        return view('Keys/list', $data);
    }

    public function downloadAllKeys()
    {
        $model = $this->model->select('user_key')->orderBy('id_keys', 'DESC');
        if ($this->user->level != 1) {
            $model->where('registrator', $this->user->username);
        }

        $rows = $model->findAll();
        $contents = implode("\n", array_map(static function ($row): string {
            $row = (array) $row;
            return (string) ($row['user_key'] ?? '');
        }, $rows));

        if ($contents !== '') {
            $contents .= "\n";
        }

        return $this->response->download(
            'legacy-keys-' . gmdate('Ymd-His') . '.txt',
            $contents,
            true
        );
    }

    public function api_get_keys()
    {
        return $this->model->API_getKeys();
    }

    public function deleteExpired()
    {
        if (strtolower($this->request->getMethod()) !== 'post') {
            return redirect()->to('keys');
        }

        $model = $this->model
            ->where('expired_date IS NOT NULL', null, false)
            ->where('expired_date <', gmdate('Y-m-d H:i:s'));
        if ($this->user->level != 1) {
            $model->where('registrator', $this->user->username);
        }
        $model->delete();

        return redirect()->to('keys')->with('msgSuccess', 'Expired legacy keys deleted.');
    }

    public function deleteUnused()
    {
        if (strtolower($this->request->getMethod()) !== 'post') {
            return redirect()->to('keys');
        }

        $model = $this->model->where('expired_date', null);
        if ($this->user->level != 1) {
            $model->where('registrator', $this->user->username);
        }
        $model->delete();

        return redirect()->to('keys')->with('msgSuccess', 'Unused legacy keys deleted.');
    }

    public function resetDevices()
    {
        if (strtolower($this->request->getMethod()) !== 'post') {
            return redirect()->to('keys');
        }

        $userKey = trim((string) $this->request->getPost('user_key'));
        $key = $this->model->getKeys($userKey);
        if (!$key || ($this->user->level != 1 && $key->registrator !== $this->user->username)) {
            return redirect()->to('keys')->with('msgDanger', 'Key not found or access denied.');
        }

        $this->model->update($key->id_keys, ['devices' => null]);
        return redirect()->to('keys')->with('msgSuccess', 'Registered devices reset.');
    }

    public function deleteKey()
    {
        if (strtolower($this->request->getMethod()) !== 'post') {
            return redirect()->to('keys');
        }

        $userKey = trim((string) $this->request->getPost('user_key'));
        $key = $this->model->getKeys($userKey);
        if (!$key || ($this->user->level != 1 && $key->registrator !== $this->user->username)) {
            return redirect()->to('keys')->with('msgDanger', 'Key not found or access denied.');
        }

        $this->model->delete($key->id_keys);
        return redirect()->to('keys')->with('msgSuccess', 'Legacy key deleted.');
    }

    public function edit_key($key = false)
    {
        if ($this->request->getPost()) return $this->edit_key_action();
        $msgDanger = "The user key no longer exists.";
        if ($key) {
            $dKey = $this->model->getKeys($key, 'id_keys');
            $user = $this->user;
            if ($dKey) {
                if ($user->level == 1 or $dKey->registrator == $user->username) {
                    $validation = Services::validation();
                    $data = [
                        'title' => 'Key',
                        'user' => $user,
                        'key' => $dKey,
                        'game_list' => $this->game_list,
                        'time' => $this->time,
                        'key_info' => getDevice($dKey->devices),
                        'messages' => setMessage('Please carefuly edit information'),
                        'validation' => $validation,
                    ];
                    return view('Keys/key_edit', $data);
                } else {
                    $msgDanger = "Restricted to this user key.";
                }
            }
        }
        return redirect()->to('keys')->with('msgDanger', $msgDanger);
    }

    private function edit_key_action()
    {
        $keys = $this->request->getPost('id_keys');
        $user = $this->user;
        $dKey = $this->model->getKeys($keys, 'id_keys');
        $game = implode(",", array_keys($this->game_list));

        if (!$dKey) {
            $msgDanger = "The user key no longer exists~";
        } else {
            if ($user->level == 1 or $dKey->registrator == $user->username) {
                $form_reseller = [
                    'status' => [
                        'label' => 'status',
                        'rules' => 'required|integer|in_list[0,1]',
                        'erros' => [
                            'integer' => 'Invalid {field}.',
                            'in_list' => 'Choose between list.'
                        ]
                    ]
                ];
                $form_admin = [
                    'id_keys' => [
                        'label' => 'keys',
                        'rules' => 'required|is_not_unique[keys_code.id_keys]|numeric',
                        'errors' => [
                            'is_not_unique' => 'Invalid keys.'
                        ],
                    ],
                    'game' => [
                        'label' => 'Games',
                        'rules' => "required|in_list[$game]",
                    ],
                    'user_key' => [
                        'label' => 'User keys',
                        'rules' => "required|min_length[4]|max_length[64]|regex_match[/^[A-Za-z0-9_-]+$/]|is_unique[keys_code.user_key,user_key,$dKey->user_key]",
                        'errors' => [
                            'is_unique' => '{field} has been taken.'
                        ],
                    ],
                    'duration' => [
                  'label' => 'duration',
                  'rules' => 'required|numeric|greater_than_equal_to[1]',
                  'errors' => [
                     'greater_than_equal_to' => 'Minimum {field} is invalid.',
                      'numeric' => 'Invalid hour {field}.'
                     ]
                    ],
                    'max_devices' => [
                        'label' => 'devices',
                        'rules' => 'required|integer|greater_than_equal_to[1]|less_than_equal_to[100]',
                        'errors' => [
                            'greater_than_equal_to' => 'Minimum {field} is invalid.',
                            'numeric' => 'Invalid max of {field}.'
                        ]
                    ],
                    'registrator' => [
                        'label' => 'registrator',
                        'rules' => 'permit_empty|alpha_numeric_space|min_length[4]'
                    ],
                    'expired_date' => [
                        'label' => 'expired',
                        'rules' => 'permit_empty|valid_date[Y-m-d H:i:s]',
                        'errors' => [
                            'valid_date' => 'Invalid {field} date.',
                        ]
                    ],
                    'devices' => [
                        'label' => 'device list',
                        'rules' => 'permit_empty'
                    ]
                ];

                if ($user->level == 1) {
                    // Admin full rules.
                    $form_rules = array_merge($form_reseller, $form_admin);
                    $devices = $this->request->getPost('devices');
                    $max_devices = $this->request->getPost('max_devices');

                    $data_saves = [
                        'game' => $this->request->getPost('game'),
                        'user_key' => $this->request->getPost('user_key'),
                        'duration' => $this->request->getPost('duration'),
                        'max_devices' => $max_devices,
                        'status' => $this->request->getPost('status'),
                        'registrator' => $this->request->getPost('registrator'),
                        'expired_date' => $this->request->getPost('expired_date') ?: NULL,
                        'devices' => setDevice($devices, $max_devices),
                    ];
                } else {
                    // Reseller just status rules, you can set manually later.
                    $form_rules = $form_reseller;
                    $data_saves = ['status' => $this->request->getPost('status')];
                }

                if (!$this->validate($form_rules)) {
                    return redirect()->back()->withInput()->with('msgDanger', 'Failed! Please check the error');
                } else {
                    // * Data Updates
                    $this->model->update($dKey->id_keys, $data_saves);
                    return redirect()->back()->with('msgSuccess', 'User key successfuly updated!');
                }
            } else {
                $msgDanger = "Restricted to this user key~";
            }
        }
        return redirect()->to('keys')->with('msgDanger', $msgDanger);
    }

    public function generate()
    {
        if ($this->request->getPost())
            return $this->generate_action();

        $user = $this->user;
        $validation = Services::validation();

        $message = setMessage("<i class='bi bi-wallet'></i> Total Saldo $$user->saldo");
        if ($user->saldo <= 0) {
            $message = setMessage("Please top up to your beloved admin.", 'warning');
        }

        $data = [
            'title' => 'Generate',
            'user' => $user,
            'time' => $this->time,
            'game' => $this->game_list,
            'duration' => $this->duration,
            'price' => json_encode($this->price),
            'messages' => $message,
            'validation' => $validation,
        ];
        return view('Keys/generate', $data);
    }


    private function generate_action()
    {
        $user = $this->user;
        $game = trim((string) $this->request->getPost('game'));
        $maxDevices = (int) $this->request->getPost('max_devices');
        $duration = (int) $this->request->getPost('duration');
        $quantity = (int) $this->request->getPost('quantity');
        $useCustom = $this->request->getPost('custominput') === 'custom';
        $customLicense = trim((string) $this->request->getPost('cuslicense'));

        $gameList = implode(',', array_keys($this->game_list));
        $formRules = [
            'game' => "required|in_list[$gameList]",
            'duration' => 'required|integer|greater_than_equal_to[1]',
            'max_devices' => 'required|integer|greater_than_equal_to[1]|less_than_equal_to[100]',
            'quantity' => 'required|integer|in_list[1,5,10,25,50,100]',
        ];

        if (!$this->validate($formRules)) {
            return redirect()->back()->withInput()
                ->with('msgDanger', 'Please correct the highlighted key fields.');
        }

        if ($useCustom) {
            if ($quantity !== 1) {
                return redirect()->back()->withInput()
                    ->with('msgDanger', 'A custom key can only be generated one at a time.');
            }
            if (preg_match('/^[A-Za-z0-9_-]{4,64}$/D', $customLicense) !== 1) {
                return redirect()->back()->withInput()->with(
                    'msgDanger',
                    'Custom keys must be 4-64 letters, numbers, underscores, or hyphens.'
                );
            }
            if ($this->model->getKeys($customLicense)) {
                return redirect()->back()->withInput()->with('msgDanger', 'That custom key already exists.');
            }
        }

        $unitPrice = (float) getPrice($this->price, $duration, $maxDevices);
        $totalPrice = $unitPrice * $quantity;
        $remainingBalance = (float) $user->saldo - $totalPrice;
        if ($unitPrice <= 0 || $remainingBalance < 0) {
            return redirect()->back()->withInput()->with(
                'msgWarning',
                'Insufficient balance for this quantity.'
            );
        }

        $generatedKeys = [];
        $history = new HistoryModel();
        $database = db_connect();
        $database->transStart();

        for ($index = 0; $index < $quantity; $index++) {
            $license = $useCustom
                ? $customLicense
                : $this->uniqueLegacyKey($user->username, $duration);
            $data = [
                'game' => $game,
                'user_key' => $license,
                'duration' => $duration,
                'max_devices' => $maxDevices,
                'registrator' => $user->username,
                'admin_id' => $this->userId,
            ];
            $idKeys = $this->model->insert($data, true);
            if (!$idKeys) {
                $database->transRollback();
                return redirect()->back()->withInput()->with(
                    'msgDanger',
                    'The key batch could not be saved.'
                );
            }

            $generatedKeys[] = $license;
            $history->insert([
                'keys_id' => $idKeys,
                'user_do' => $user->username,
                'info' => "$game|" . substr($license, 0, 5) . "|$duration|$maxDevices",
            ]);
        }

        $this->userModel->update(session('userid'), ['saldo' => $remainingBalance]);
        $database->transComplete();
        if ($database->transStatus() === false) {
            return redirect()->back()->withInput()->with(
                'msgDanger',
                'The key transaction failed and was rolled back.'
            );
        }

        session()->setFlashdata([
            'generated_keys' => $generatedKeys,
            'user_key' => $generatedKeys[0],
            'game' => $game,
            'duration' => $duration,
            'max_devices' => $maxDevices,
            'quantity' => $quantity,
            'fees' => $totalPrice,
        ]);

        return redirect()->back()->with(
            'msgSuccess',
            count($generatedKeys) . ' legacy key(s) generated successfully.'
        );
    }

    private function uniqueLegacyKey(string $username, int $duration): string
    {
        for ($attempt = 0; $attempt < 10; $attempt++) {
            $candidate = $username . '-' . $duration . '-' . random_string('alnum', 8);
            if (!$this->model->getKeys($candidate)) {
                return $candidate;
            }
        }

        throw new \RuntimeException('Unable to generate a unique legacy key.');
    }

}
