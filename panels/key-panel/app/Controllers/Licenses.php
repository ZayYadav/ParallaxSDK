<?php

namespace App\Controllers;

use App\Models\IntegrityLicenseModel;
use App\Models\UserModel;
use CodeIgniter\I18n\Time;
use Config\Services;
use Throwable;

class Licenses extends BaseController
{
    private UserModel $userModel;
    private $user;

    public function __construct()
    {
        $this->userModel = new UserModel();
        $this->user = $this->userModel->getUser();
    }

    public function index()
    {
        $rows = [];
        $metrics = ['total' => 0, 'active' => 0, 'revoked' => 0, 'expired' => 0];
        $configurationError = null;

        try {
            $model = new IntegrityLicenseModel();
            $rows = $model->dashboardRows();
            $metrics = $model->metrics();
        } catch (Throwable $throwable) {
            log_message('error', 'Integrity license dashboard: {message}', [
                'message' => $throwable->getMessage(),
            ]);
            $configurationError = 'OneCore Integrity database is not configured or its license tables are unavailable.';
        }

        return view('Licenses/index', [
            'title' => 'OneCore Licenses',
            'user' => $this->user,
            'time' => new Time(),
            'licenses' => $rows,
            'metrics' => $metrics,
            'configurationError' => $configurationError,
            'validation' => Services::validation(),
        ]);
    }

    public function create()
    {
        if (strtolower($this->request->getMethod()) !== 'post') {
            return redirect()->to('licenses');
        }

        $rules = [
            'label' => 'required|max_length[100]',
            'max_devices' => 'required|integer|greater_than_equal_to[1]|less_than_equal_to[100]',
            'valid_days' => 'required|integer|greater_than_equal_to[1]|less_than_equal_to[3650]',
            'quantity' => 'required|integer|in_list[1,5,10,25,50,100]',
        ];

        if (!$this->validate($rules)) {
            return redirect()->back()->withInput()
                ->with('msgDanger', 'Please correct the highlighted license fields.');
        }

        try {
            $keys = (new IntegrityLicenseModel())->createActivationKeys(
                trim((string) $this->request->getPost('label')),
                (int) $this->request->getPost('max_devices'),
                (int) $this->request->getPost('valid_days'),
                (int) $this->request->getPost('quantity')
            );
        } catch (Throwable $throwable) {
            log_message('error', 'Integrity license creation: {message}', [
                'message' => $throwable->getMessage(),
            ]);
            return redirect()->back()->withInput()->with(
                'msgDanger',
                'Activation keys could not be created. Check the Integrity database configuration.'
            );
        }

        session()->setFlashdata('created_activation_keys', $keys);
        return redirect()->to('licenses')->with(
            'msgSuccess',
            count($keys) . ' OneCore activation key(s) created. Copy them now; only hashes are stored.'
        );
    }

    public function revoke()
    {
        if (strtolower($this->request->getMethod()) !== 'post') {
            return redirect()->to('licenses');
        }

        $licenseId = filter_var($this->request->getPost('license_id'), FILTER_VALIDATE_INT);
        if ($licenseId === false || $licenseId < 1) {
            return redirect()->to('licenses')->with('msgDanger', 'Invalid license ID.');
        }

        try {
            $revoked = (new IntegrityLicenseModel())->revokeWithDevices($licenseId);
        } catch (Throwable $throwable) {
            log_message('error', 'Integrity license revocation: {message}', [
                'message' => $throwable->getMessage(),
            ]);
            $revoked = false;
        }

        return redirect()->to('licenses')->with(
            $revoked ? 'msgSuccess' : 'msgDanger',
            $revoked
                ? 'License and its bound devices were revoked.'
                : 'The license could not be revoked.'
        );
    }
}
