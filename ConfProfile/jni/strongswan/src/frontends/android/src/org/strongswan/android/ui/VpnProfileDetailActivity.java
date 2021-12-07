/*
 * Copyright (C) 2012 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.ui;

import java.security.cert.X509Certificate;

import org.strongswan.android.R;
import org.strongswan.android.data.TrustedCertificateEntry;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.logic.TrustedCertificateManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TwoLineListItem;

public class VpnProfileDetailActivity extends Activity
{
	private static final int SELECT_TRUSTED_CERTIFICATE = 0;

	private VpnProfileDataSource mDataSource;
	private Long mId;
	private TrustedCertificateEntry mCertEntry;
	private String mUserCertLoading;
	private TrustedCertificateEntry mUserCertEntry;
	private VpnType mVpnType = VpnType.IKEV2_EAP;
	private VpnProfile mProfile;
	private EditText mName;
	private EditText mGateway;
	private Spinner mSelectVpnType;
	private ViewGroup mUsernamePassword;
	private EditText mUsername;
	private EditText mPassword;
	private ViewGroup mUserCertificate;
	private TwoLineListItem mSelectUserCert;
	private CheckBox mCheckAuto;
	private TwoLineListItem mSelectCert;
	private TwoLineListItem mTncNotice;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		/* the title is set when we load the profile, if any */
		getActionBar().setDisplayHomeAsUpEnabled(true);

		mDataSource = new VpnProfileDataSource(this);
		mDataSource.open();

		setContentView(R.layout.profile_detail_view);

		mName = (EditText)findViewById(R.id.name);
		mGateway = (EditText)findViewById(R.id.gateway);
		mSelectVpnType = (Spinner)findViewById(R.id.vpn_type);
		mTncNotice = (TwoLineListItem)findViewById(R.id.tnc_notice);

		mUsernamePassword = (ViewGroup)findViewById(R.id.username_password_group);
		mUsername = (EditText)findViewById(R.id.username);
		mPassword = (EditText)findViewById(R.id.password);

		mUserCertificate = (ViewGroup)findViewById(R.id.user_certificate_group);
		mSelectUserCert = (TwoLineListItem)findViewById(R.id.select_user_certificate);

		mCheckAuto = (CheckBox)findViewById(R.id.ca_auto);
		mSelectCert = (TwoLineListItem)findViewById(R.id.select_certificate);

		mSelectVpnType.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				mVpnType = VpnType.values()[position];
				updateCredentialView();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent)
			{	/* should not happen */
				mVpnType = VpnType.IKEV2_EAP;
				updateCredentialView();
			}
		});

		mTncNotice.getText1().setText(R.string.tnc_notice_title);
		mTncNotice.getText2().setText(R.string.tnc_notice_subtitle);
		mTncNotice.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v)
			{
				new TncNoticeDialog().show(VpnProfileDetailActivity.this.getFragmentManager(), "TncNotice");
			}
		});

		mSelectUserCert.setOnClickListener(new SelectUserCertOnClickListener());

		mCheckAuto.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				updateCertificateSelector();
			}
		});

		mSelectCert.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v)
			{
				Intent intent = new Intent(VpnProfileDetailActivity.this, TrustedCertificatesActivity.class);
				startActivityForResult(intent, SELECT_TRUSTED_CERTIFICATE);
			}
		});

		mId = savedInstanceState == null ? null : savedInstanceState.getLong(VpnProfileDataSource.KEY_ID);
		if (mId == null)
		{
			Bundle extras = getIntent().getExtras();
			mId = extras == null ? null : extras.getLong(VpnProfileDataSource.KEY_ID);
		}

		loadProfileData(savedInstanceState);

		updateCredentialView();
		updateCertificateSelector();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mDataSource.close();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (mId != null)
		{
			outState.putLong(VpnProfileDataSource.KEY_ID, mId);
		}
		if (mUserCertEntry != null)
		{
			outState.putString(VpnProfileDataSource.KEY_USER_CERTIFICATE, mUserCertEntry.getAlias());
		}
		if (mCertEntry != null)
		{
			outState.putString(VpnProfileDataSource.KEY_CERTIFICATE, mCertEntry.getAlias());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.profile_edit, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
			case R.id.menu_cancel:
				finish();
				return true;
			case R.id.menu_accept:
				saveProfile();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case SELECT_TRUSTED_CERTIFICATE:
				if (resultCode == RESULT_OK)
				{
					String alias = data.getStringExtra(VpnProfileDataSource.KEY_CERTIFICATE);
					X509Certificate certificate = TrustedCertificateManager.getInstance().getCACertificateFromAlias(alias);
					mCertEntry = certificate == null ? null : new TrustedCertificateEntry(alias, certificate);
					updateCertificateSelector();
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	/**
	 * Update the UI to enter credentials depending on the type of VPN currently selected
	 */
	private void updateCredentialView()
	{
		mUsernamePassword.setVisibility(mVpnType.getRequiresUsernamePassword() ? View.VISIBLE : View.GONE);
		mUserCertificate.setVisibility(mVpnType.getRequiresCertificate() ? View.VISIBLE : View.GONE);
		mTncNotice.setVisibility(mVpnType.getEnableBYOD() ? View.VISIBLE : View.GONE);

		if (mVpnType.getRequiresCertificate())
		{
			if (mUserCertLoading != null)
			{
				mSelectUserCert.getText1().setText(mUserCertLoading);
				mSelectUserCert.getText2().setText(R.string.loading);
			}
			else if (mUserCertEntry != null)
			{	/* clear any errors and set the new data */
				mSelectUserCert.getText1().setError(null);
				mSelectUserCert.getText1().setText(mUserCertEntry.getAlias());
				mSelectUserCert.getText2().setText(mUserCertEntry.getCertificate().getSubjectDN().toString());
			}
			else
			{
				mSelectUserCert.getText1().setText(R.string.profile_user_select_certificate_label);
				mSelectUserCert.getText2().setText(R.string.profile_user_select_certificate);
			}
		}
	}

	/**
	 * Show an alert in case the previously selected certificate is not found anymore
	 * or the user did not select a certificate in the spinner.
	 */
	private void showCertificateAlert()
	{
		AlertDialog.Builder adb = new AlertDialog.Builder(VpnProfileDetailActivity.this);
		adb.setTitle(R.string.alert_text_nocertfound_title);
		adb.setMessage(R.string.alert_text_nocertfound);
		adb.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
			}
		});
		adb.show();
	}

	/**
	 * Update the CA certificate selection UI depending on whether the
	 * certificate should be automatically selected or not.
	 */
	private void updateCertificateSelector()
	{
		if (!mCheckAuto.isChecked())
		{
			mSelectCert.setEnabled(true);
			mSelectCert.setVisibility(View.VISIBLE);

			if (mCertEntry != null)
			{
				mSelectCert.getText1().setText(mCertEntry.getSubjectPrimary());
				mSelectCert.getText2().setText(mCertEntry.getSubjectSecondary());
			}
			else
			{
				mSelectCert.getText1().setText(R.string.profile_ca_select_certificate_label);
				mSelectCert.getText2().setText(R.string.profile_ca_select_certificate);
			}
		}
		else
		{
			mSelectCert.setEnabled(false);
			mSelectCert.setVisibility(View.GONE);
		}
	}

	/**
	 * Save or update the profile depending on whether we actually have a
	 * profile object or not (this was created in updateProfileData)
	 */
	private void saveProfile()
	{
		if (verifyInput())
		{
			if (mProfile != null)
			{
				updateProfileData();
				mDataSource.updateVpnProfile(mProfile);
			}
			else
			{
				mProfile = new VpnProfile();
				updateProfileData();
				mDataSource.insertProfile(mProfile);
			}
			setResult(RESULT_OK, new Intent().putExtra(VpnProfileDataSource.KEY_ID, mProfile.getId()));
			finish();
		}
	}

	/**
	 * Verify the user input and display error messages.
	 * @return true if the input is valid
	 */
	private boolean verifyInput()
	{
		boolean valid = true;
		if (mGateway.getText().toString().trim().isEmpty())
		{
			mGateway.setError(getString(R.string.alert_text_no_input_gateway));
			valid = false;
		}
		if (mVpnType.getRequiresUsernamePassword())
		{
			if (mUsername.getText().toString().trim().isEmpty())
			{
				mUsername.setError(getString(R.string.alert_text_no_input_username));
				valid = false;
			}
		}
		if (mVpnType.getRequiresCertificate() && mUserCertEntry == null)
		{	/* let's show an error icon */
			mSelectUserCert.getText1().setError("");
			valid = false;
		}
		if (!mCheckAuto.isChecked() && mCertEntry == null)
		{
			showCertificateAlert();
			valid = false;
		}
		return valid;
	}

	/**
	 * Update the profile object with the data entered by the user
	 */
	private void updateProfileData()
	{
		/* the name is optional, we default to the gateway if none is given */
		String name = mName.getText().toString().trim();
		String gateway = mGateway.getText().toString().trim();
		mProfile.setName(name.isEmpty() ? gateway : name);
		mProfile.setGateway(gateway);
		mProfile.setVpnType(mVpnType);
		if (mVpnType.getRequiresUsernamePassword())
		{
			mProfile.setUsername(mUsername.getText().toString().trim());
			String password = mPassword.getText().toString().trim();
			password = password.isEmpty() ? null : password;
			mProfile.setPassword(password);
		}
		if (mVpnType.getRequiresCertificate())
		{
			mProfile.setUserCertificateAlias(mUserCertEntry.getAlias());
		}
		String certAlias = mCheckAuto.isChecked() ? null : mCertEntry.getAlias();
		mProfile.setCertificateAlias(certAlias);
	}

	/**
	 * Load an existing profile if we got an ID
	 *
	 * @param savedInstanceState previously saved state
	 */
	private void loadProfileData(Bundle savedInstanceState)
	{
		String useralias = null, alias = null;

		getActionBar().setTitle(R.string.add_profile);
		if (mId != null && mId != 0)
		{
			mProfile = mDataSource.getVpnProfile(mId);
			if (mProfile != null)
			{
				mName.setText(mProfile.getName());
				mGateway.setText(mProfile.getGateway());
				mVpnType = mProfile.getVpnType();
				mUsername.setText(mProfile.getUsername());
				mPassword.setText(mProfile.getPassword());
				useralias = mProfile.getUserCertificateAlias();
				alias = mProfile.getCertificateAlias();
				getActionBar().setTitle(mProfile.getName());
			}
			else
			{
				Log.e(VpnProfileDetailActivity.class.getSimpleName(),
					  "VPN profile with id " + mId + " not found");
				finish();
			}
		}

		mSelectVpnType.setSelection(mVpnType.ordinal());

		/* check if the user selected a user certificate previously */
		useralias = savedInstanceState == null ? useralias: savedInstanceState.getString(VpnProfileDataSource.KEY_USER_CERTIFICATE);
		if (useralias != null)
		{
			UserCertificateLoader loader = new UserCertificateLoader(this, useralias);
			mUserCertLoading = useralias;
			loader.execute();
		}

		/* check if the user selected a CA certificate previously */
		alias = savedInstanceState == null ? alias : savedInstanceState.getString(VpnProfileDataSource.KEY_CERTIFICATE);
		mCheckAuto.setChecked(alias == null);
		if (alias != null)
		{
			X509Certificate certificate = TrustedCertificateManager.getInstance().getCACertificateFromAlias(alias);
			if (certificate != null)
			{
				mCertEntry = new TrustedCertificateEntry(alias, certificate);
			}
			else
			{	/* previously selected certificate is not here anymore */
				showCertificateAlert();
				mCertEntry = null;
			}
		}
	}

	private class SelectUserCertOnClickListener implements OnClickListener, KeyChainAliasCallback
	{
		@Override
		public void onClick(View v)
		{
			String useralias = mUserCertEntry != null ? mUserCertEntry.getAlias() : null;
			KeyChain.choosePrivateKeyAlias(VpnProfileDetailActivity.this, this, new String[] { "RSA" }, null, null, -1, useralias);
		}

		@Override
		public void alias(final String alias)
		{
			if (alias != null)
			{	/* otherwise the dialog was canceled, the request denied */
				try
				{
					final X509Certificate[] chain = KeyChain.getCertificateChain(VpnProfileDetailActivity.this, alias);
					/* alias() is not called from our main thread */
					runOnUiThread(new Runnable() {
						@Override
						public void run()
						{
							if (chain != null && chain.length > 0)
							{
								mUserCertEntry = new TrustedCertificateEntry(alias, chain[0]);
							}
							updateCredentialView();
						}
					});
				}
				catch (KeyChainException e)
				{
					e.printStackTrace();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Load the selected user certificate asynchronously.  This cannot be done
	 * from the main thread as getCertificateChain() calls back to our main
	 * thread to bind to the KeyChain service resulting in a deadlock.
	 */
	private class UserCertificateLoader extends AsyncTask<Void, Void, X509Certificate>
	{
		private final Context mContext;
		private final String mAlias;

		public UserCertificateLoader(Context context, String alias)
		{
			mContext = context;
			mAlias = alias;
		}

		@Override
		protected X509Certificate doInBackground(Void... params)
		{
			X509Certificate[] chain = null;
			try
			{
				chain = KeyChain.getCertificateChain(mContext, mAlias);
			}
			catch (KeyChainException e)
			{
				e.printStackTrace();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			if (chain != null && chain.length > 0)
			{
				return chain[0];
			}
			return null;
		}

		@Override
		protected void onPostExecute(X509Certificate result)
		{
			if (result != null)
			{
				mUserCertEntry = new TrustedCertificateEntry(mAlias, result);
			}
			else
			{	/* previously selected certificate is not here anymore */
				mSelectUserCert.getText1().setError("");
				mUserCertEntry = null;
			}
			mUserCertLoading = null;
			updateCredentialView();
		}
	}

	/**
	 * Dialog with notification message if EAP-TNC is used.
	 */
	public static class TncNoticeDialog extends DialogFragment
	{
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			return new AlertDialog.Builder(getActivity())
				.setTitle(R.string.tnc_notice_title)
				.setMessage(Html.fromHtml(getString(R.string.tnc_notice_details)))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						dialog.dismiss();
					}
				}).create();
		}
	}
}
