/*
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.leanfront.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.RecordRule;
import org.mythtv.leanfront.model.Video;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class EditScheduleFragment extends GuidedStepSupportFragment implements AsyncBackendCall.OnBackendCallListener {

    private RecordRule mProgDetails;
    private RecordRule mRecordRule;
    private ArrayList<XmlNode> mDetailsList;
    private ArrayList<RecordRule> mTemplateList = new ArrayList<>();
    private ArrayList<String> mPlayGroupList;
    private ArrayList<String> mRecGroupList;
    private ArrayList<String> mRecStorageGroupList;
    private SparseArray<String> mInputList = new SparseArray<>();
    private ArrayList<String> mRecRuleFilterList;
    private int mGroupId;
    private ArrayList<ActionGroup> mGroupList = new ArrayList<>();
    private String mNewValueText;
    private boolean mIsDirty;
    private int mRecordId;

    private ActionGroup mGpType;
    private ActionGroup mGpRecGroup;
    private ActionGroup mGpActive;
    private ActionGroup mGpPlayGroup;
    private ActionGroup mGpStartOffset;
    private ActionGroup mGpEndOffset;
    private ActionGroup mGpNewEpisOnly;
    private ActionGroup mGpRecPriority;
    private ActionGroup mGpPreferredInput;
    private ActionGroup mGpDupMethod;
    private ActionGroup mGpDupIn;
    private ActionGroup mGpFilter;
    private ActionGroup mGpRecProfile;
    private ActionGroup mGpStorageGroup;
    private ActionGroup mGpMaxEpisodes;
    private ActionGroup mGpMaxNewest;
    private ActionGroup mGpAutoExpire;
    private ActionGroup mGpPostProc;
    private ActionGroup mGpInetRefType;
    private ActionGroup mGpInetRefNum;
    private ActionGroup mGpUseTemplate;
    private ActionGroup mGpSaveButton;
    private ActionGroup mGpCancelButton;

    private static DateFormat timeFormatter;
    private static DateFormat dateFormatter;
    private static DateFormat dayFormatter;

    private static final int ACTIONTYPE_RADIOBNS = 1;
    private static final int ACTIONTYPE_CHECKBOXES = 2;
    private static final int ACTIONTYPE_NUMERIC = 3;
    private static final int ACTIONTYPE_NUMERIC_UNSIGNED = 4;
    private static final int ACTIONTYPE_BOOLEAN = 5;
    private static final int ACTIONTYPE_BUTTON = 6;
    private static final int ACTIONTYPE_BUTTONS = 7;

    private static final String TAG = "lfe";
    private static final String CLASS = "EditScheduleFragment";


    public EditScheduleFragment(ArrayList<XmlNode> detailsList, int recordId) {
        /*
            Details are in this order
                Video.ACTION_GETPROGRAMDETAILS,
                Video.ACTION_GETRECORDSCHEDULELIST,
                Video.ACTION_GETPLAYGROUPLIST,
                Video.ACTION_GETRECGROUPLIST,
                Video.ACTION_GETRECSTORAGEGROUPLIST,
                Video.ACTION_GETINPUTLIST
                Video.ACTION_GETRECRULEFILTERLIST
         */
        mDetailsList = detailsList;
        mRecordId= recordId;
    }

    private void setupData() {
        mIsDirty = false;
        RecordRule defaultTemplate = null;
        if (mRecordId == 0) {
            XmlNode progDetailsNode = mDetailsList.get(0); // ACTION_GETPROGRAMDETAILS
            if (progDetailsNode != null) {
                mProgDetails = new RecordRule().fromProgram(progDetailsNode);
                mRecordId = mProgDetails.recordId;
            }
        }

        XmlNode recRulesNode = mDetailsList.get(1) // ACTION_GETRECORDSCHEDULELIST
                .getNode("RecRules");
        if (recRulesNode != null) {
            XmlNode recRuleNode = recRulesNode.getNode("RecRule");
            while (recRuleNode != null) {
                int id = Integer.parseInt(recRuleNode.getString("Id"));
                if (id == mRecordId)
                    mRecordRule = new RecordRule().fromSchedule(recRuleNode);
                String type = recRuleNode.getString("Type");
                if ("Recording Template".equals(type)) {
                    RecordRule template = new RecordRule().fromSchedule(recRuleNode);
                    mTemplateList.add(template);
                    if ("Default (Template)".equals(template.title))
                        defaultTemplate = template;
                }
                recRuleNode = recRuleNode.getNextSibling();
            }
        }
        if (mRecordRule == null) {
            if (mRecordId != 0) {
                // Record no longer exists
                Toast.makeText(getContext(),R.string.msg_rec_rule_gone, Toast.LENGTH_LONG)
                        .show();
            }
            mRecordRule = new RecordRule().mergeTemplate(defaultTemplate);
        }
        if (mProgDetails != null)
            mRecordRule.mergeProgram(mProgDetails);
        if (mRecordRule.type == null)
            mRecordRule.type = "Not Recording";

        // Lists
        mPlayGroupList = getStringList(mDetailsList.get(2)); // ACTION_GETPLAYGROUPLIST
        mRecGroupList = getStringList(mDetailsList.get(3)); // ACTION_GETRECGROUPLIST
        mRecStorageGroupList = getStringList(mDetailsList.get(4)); // ACTION_GETRECSTORAGEGROUPLIST

        mInputList.put(0, getContext().getString(R.string.sched_input_any));
        XmlNode inputListNode = mDetailsList.get(5); // ACTION_GETINPUTLIST
        if (inputListNode != null) {
            XmlNode inputsNode = inputListNode.getNode("Inputs");
            if (inputsNode != null) {
                XmlNode inputNode = inputsNode.getNode("Input");
                while (inputNode != null) {
                    int id = inputNode.getInt("Id",-1);
                    String displayName = inputNode.getString("DisplayName");
                    if (id > 0)
                        mInputList.put(id, displayName);
                    inputNode = inputNode.getNextSibling();
                }
            }
        }
        mRecRuleFilterList = new ArrayList<>();
        XmlNode filterListNode = mDetailsList.get(6); // ACTION_GETRECRULEFILTERLIST
        if (filterListNode != null) {
            XmlNode filtersNode = filterListNode.getNode("RecRuleFilters");
            if (filtersNode != null) {
                XmlNode filterNode = filtersNode.getNode("RecRuleFilter");
                while (filterNode != null) {
                    int id = filterNode.getInt("Id",-1);
                    String description = filterNode.getString("Description");
                    for (int ix = mRecRuleFilterList.size(); ix <= id; ix++)
                        mRecRuleFilterList.add(null);
                    if (id >= 0)
                        mRecRuleFilterList.set(id, description);
                    filterNode = filterNode.getNextSibling();
                }
            }
        }
    }


    public static ArrayList<String> getStringList(XmlNode listNode) {
        ArrayList<String> ret = new ArrayList<>();
        ret.add("Default");
        if (listNode != null) {
            XmlNode stringNode = listNode.getNode("String");
            while (stringNode != null) {
                String value = stringNode.getString();
                if (!"Default".equals(value))
                    ret.add(value);
                stringNode = stringNode.getNextSibling();
            }
        }
        return ret;
    }


    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {

        if (timeFormatter == null) {
            timeFormatter = android.text.format.DateFormat.getTimeFormat(getContext());
            dateFormatter = android.text.format.DateFormat.getLongDateFormat(getContext());
            dayFormatter = new SimpleDateFormat("EEE ");
        }
        if (mRecordRule == null)
            setupData();
        if (mRecordRule == null)
            return new GuidanceStylist.Guidance(null,null,null,null);
        Activity activity = getActivity();
        String title = mRecordRule.title;
        StringBuilder dateTime = new StringBuilder();
        if (mRecordRule.station != null)
        dateTime.append(mRecordRule.station).append(' ');
        dateTime.append(dayFormatter.format(mRecordRule.startTime))
                .append(dateFormatter.format(mRecordRule.startTime)).append(' ')
                .append(timeFormatter.format(mRecordRule.startTime));
        StringBuilder details = new StringBuilder();
        String subTitle = mRecordRule.subtitle;
        if (subTitle != null)
            details.append(subTitle).append("\n");
        String desc = mRecordRule.description;
        if (desc != null)
            details.append(desc);
        Drawable icon = activity.getDrawable(R.drawable.ic_voicemail);
        return new GuidanceStylist.Guidance(title, details.toString(), dateTime.toString(), icon);
    }

    static final int[] sRepeatPrompts = {
            R.string.sched_new_and_repeat, R.string.sched_new_only};
    static final int[] sActivePrompts = {
            R.string.sched_inactive_msg, R.string.sched_active_msg};
    static final int [] sDupMethodPrompts = {
            R.string.sched_dup_none, R.string.sched_dup_s_and_d,
            R.string.sched_dup_s_then_d, R.string.sched_dup_s,
            R.string.sched_dup_d };
    static final String [] sDupMethodValues = {
            "None", "Subtitle and Description",
            "Subtitle then Description", "Subtitle",
            "Description" };
    static final int [] sDupScopePrompts = {
            R.string.sched_dup_both, R.string.sched_dup_curr,
            R.string.sched_dup_prev };
    static final String [] sDupScopeValues = {
            "All Recordings", "Current Recordings",
            "Previous Recordings" };
    static final int [] sRecProfilePrompts = {
            R.string.sched_recprof_default, R.string.sched_recprof_livetv,
            R.string.sched_recprof_highq, R.string.sched_recprof_lowq };
    static final String [] sRecProfileValues = {
            "Default", "Live TV",
            "High Quality", "Low Quality" };
    static final int [] sNewestPrompts = {
            R.string.sched_max_dont,
            R.string.sched_max_delete};
    static final int [] sAutoExpirePrompts = {
            R.string.sched_auto_expire_off, R.string.sched_auto_expire_on};
    static final int [] sPostProcPrompts = {
            R.string.sched_pp_commflag, R.string.sched_pp_metadata,
            R.string.sched_pp_transcode,
            R.string.sched_pp_job1, R.string.sched_pp_job2,
            R.string.sched_pp_job3, R.string.sched_pp_job4};
    static final int [] sMetaDataPrompts = {
            R.string.sched_metadata_type_tv, R.string.sched_metadata_type_movie };

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> mainActions, Bundle savedInstanceState) {
        if (mRecordRule == null)
            setupData();
        if (mRecordRule == null)
            return;
        int ix;

        // Type options depend on what is being done
        // Logic from ScheduleEditor::Load, values from recordintypes.cpp
        // toDescription(RecordingType rectype) and toRawString(RecordingType rectype)
        ArrayList<Integer> typePrompts = new ArrayList<>();
        ArrayList<String> typeOptions = new ArrayList<>();

        if ("Recording Template".equalsIgnoreCase(mRecordRule.type)) {
            if (! "Default".equalsIgnoreCase(mRecordRule.category)) {
                typePrompts.add(R.string.sched_type_del_template);
                typeOptions.add("Not Recording");
            }
            typePrompts.add(R.string.sched_type_mod_template);
            typeOptions.add("Recording Template");
        }
        else if ("Override Recording".equalsIgnoreCase(mRecordRule.type)) {
            typePrompts.add(R.string.sched_type_del_override);
            typeOptions.add("Not Recording");
            typePrompts.add(R.string.sched_type_rec_override);
            typeOptions.add("Override Recording");
            typePrompts.add(R.string.sched_type_dont_rec_override);
            typeOptions.add("Do not Record");
        }
        else {
            boolean hasChannel = (mRecordRule.station != null);
            boolean isManual = "Manual Search".equalsIgnoreCase(mRecordRule.searchType);
            typePrompts.add(R.string.sched_type_not);
            typeOptions.add("Not Recording");
            if (hasChannel) {
                typePrompts.add(R.string.sched_type_this);
                typeOptions.add("Single Record");
            }
            if (!isManual) {
                typePrompts.add(R.string.sched_type_one);
                typeOptions.add("Record One");
            }
            if (!hasChannel || isManual) {
                typePrompts.add(R.string.sched_type_weekly);
                typeOptions.add("Record Weekly");
                typePrompts.add(R.string.sched_type_daily);
                typeOptions.add("Record Daily");
            }
            if (!isManual) {
                typePrompts.add(R.string.sched_type_all);
                typeOptions.add("Record All");
            }
        }
        int [] intTypePrompts = new int[typePrompts.size()];
        for (ix = 0; ix < intTypePrompts.length; ix++)
            intTypePrompts[ix] = typePrompts.get(ix);
        // Type
        mGpType = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_type,
                intTypePrompts, typeOptions.toArray(new String[typeOptions.size()]),
                mRecordRule.type, false);
        mainActions.add(mGpType.mGuidedAction);
        mGroupList.add(mGpType);

        // Recording Group
        mGpRecGroup = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_rec_group,
                null, mRecGroupList.toArray(new String[mRecGroupList.size()+1]),
                mRecordRule.recGroup, true);
        mainActions.add(mGpRecGroup.mGuidedAction);
        mGroupList.add(mGpRecGroup);

        // Active
        mGpActive = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_active,
                sActivePrompts, ! mRecordRule.inactive);
        mainActions.add(mGpActive.mGuidedAction);
        mGroupList.add(mGpActive);

        // Playback Group
        mGpPlayGroup = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_play_group,
                null, mPlayGroupList.toArray(new String[mPlayGroupList.size()]),
                mRecordRule.playGroup, false);
        mainActions.add(mGpPlayGroup.mGuidedAction);
        mGroupList.add(mGpPlayGroup);

        // Start Offset
        mGpStartOffset = new ActionGroup(ACTIONTYPE_NUMERIC, R.string.sched_start_offset,
                mRecordRule.startOffset);
        mainActions.add(mGpStartOffset.mGuidedAction);
        mGroupList.add(mGpStartOffset);

        // End Offset
        mGpEndOffset = new ActionGroup(ACTIONTYPE_NUMERIC, R.string.sched_end_offset,
                mRecordRule.endOffset);
        mainActions.add(mGpEndOffset.mGuidedAction);
        mGroupList.add(mGpEndOffset);

        // New Episodes Only
        mGpNewEpisOnly = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_repeats,
                sRepeatPrompts, mRecordRule.newEpisOnly);
        mainActions.add(mGpNewEpisOnly.mGuidedAction);
        mGroupList.add(mGpNewEpisOnly);
        if (mDetailsList.get(1).getNode("RecRules")
                .getNode("RecRule").getNode("NewEpisOnly") == null) {
            mGpNewEpisOnly.mGuidedAction.setEnabled(false);
            mGpNewEpisOnly.mGuidedAction.setDescription
                    (getContext().getString(R.string.sched_new_unsupported));
        }

        // Record Priority
        mGpRecPriority = new ActionGroup(ACTIONTYPE_NUMERIC, R.string.sched_priority,
                mRecordRule.recPriority);
        mainActions.add(mGpRecPriority.mGuidedAction);
        mGroupList.add(mGpRecPriority);

        // Preferred Input
        String [] stringPrompts = new String[mInputList.size()];
        int [] intValues = new int[mInputList.size()];
        for (ix = 0 ; ix < mInputList.size(); ix++) {
            stringPrompts[ix] = mInputList.valueAt(ix);
            intValues[ix] = mInputList.keyAt(ix);
        }
        mGpPreferredInput = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_input,
                 stringPrompts, intValues, mRecordRule.preferredInput);
        mainActions.add(mGpPreferredInput.mGuidedAction);
        mGroupList.add(mGpPreferredInput);

        // Duplicate Match Method
        mGpDupMethod = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_dupmethod,
                sDupMethodPrompts, sDupMethodValues, mRecordRule.dupMethod, false);
        mainActions.add(mGpDupMethod.mGuidedAction);
        mGroupList.add(mGpDupMethod);

        // Dup Scope
        mGpDupIn = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_dupscope,
                sDupScopePrompts, sDupScopeValues, mRecordRule.dupIn, false);
        mainActions.add(mGpDupIn.mGuidedAction);
        mGroupList.add(mGpDupIn);

        // Filters
        stringPrompts = new String[mRecRuleFilterList.size()];
        stringPrompts = mRecRuleFilterList.toArray(stringPrompts);
        mGpFilter = new ActionGroup(ACTIONTYPE_CHECKBOXES, R.string.sched_filters,
                null, stringPrompts, mRecordRule.filter);
        mainActions.add(mGpFilter.mGuidedAction);
        mGroupList.add(mGpFilter);

        // Recording profile
        mGpRecProfile = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_rec_profile,
                sRecProfilePrompts, sRecProfileValues, mRecordRule.recProfile, false);
        mainActions.add(mGpRecProfile.mGuidedAction);
        mGroupList.add(mGpRecProfile);

        // Storage Group
        mGpStorageGroup = new ActionGroup(ACTIONTYPE_RADIOBNS, R.string.sched_storage_grp,
                null, mRecStorageGroupList.toArray(new String[mRecStorageGroupList.size()]),
                mRecordRule.storageGroup, false);
        mainActions.add(mGpStorageGroup.mGuidedAction);
        mGroupList.add(mGpStorageGroup);

        // Max to keep
        mGpMaxEpisodes = new ActionGroup(ACTIONTYPE_NUMERIC_UNSIGNED, R.string.sched_max_to_keep,
                mRecordRule.maxEpisodes);
        mainActions.add(mGpMaxEpisodes.mGuidedAction);
        mGroupList.add(mGpMaxEpisodes);

        // Max Newest
        mGpMaxNewest = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_max_newest,
                sNewestPrompts, mRecordRule.maxNewest);
        mainActions.add(mGpMaxNewest.mGuidedAction);
        mGroupList.add(mGpMaxNewest);

        // Auto Expire
        mGpAutoExpire = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_auto_expire,
                sAutoExpirePrompts, mRecordRule.autoExpire);
        mainActions.add(mGpAutoExpire.mGuidedAction);
        mGroupList.add(mGpAutoExpire);

        // post Processing
        int ppVal = 0;
        ppVal |= mRecordRule.autoCommflag   ? 1 << 0 : 0;
        ppVal |= mRecordRule.autoMetaLookup ? 1 << 1 : 0;
        ppVal |= mRecordRule.autoTranscode  ? 1 << 2 : 0;
        ppVal |= mRecordRule.autoUserJob1   ? 1 << 3 : 0;
        ppVal |= mRecordRule.autoUserJob2   ? 1 << 4 : 0;
        ppVal |= mRecordRule.autoUserJob3   ? 1 << 5 : 0;
        ppVal |= mRecordRule.autoUserJob4   ? 1 << 6 : 0;

        mGpPostProc = new ActionGroup(ACTIONTYPE_CHECKBOXES, R.string.sched_pp_title,
                sPostProcPrompts, (String[])null, ppVal);
        mainActions.add(mGpPostProc.mGuidedAction);
        mGroupList.add(mGpPostProc);

        // Metadata type ttvdb.py_ or tmdb3.py_
        boolean mdVal = mRecordRule.inetref != null && mRecordRule.inetref.startsWith("tmdb3.py_");

        mGpInetRefType = new ActionGroup(ACTIONTYPE_BOOLEAN, R.string.sched_metadata_type,
                sMetaDataPrompts, mdVal);
        mainActions.add(mGpInetRefType.mGuidedAction);
        mGroupList.add(mGpInetRefType);

        // Metadata number
        int id = 0;
        if (mRecordRule.inetref != null) {
            String [] parts = mRecordRule.inetref.split("_");
            if (parts.length == 2) {
                try {
                    id = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    id = 0;
                }
            }
        }
        mGpInetRefNum = new ActionGroup(ACTIONTYPE_NUMERIC_UNSIGNED, R.string.sched_metadata_id,
                id);
        mainActions.add(mGpInetRefNum.mGuidedAction);
        mGroupList.add(mGpInetRefNum);

        // Use Template
        stringPrompts = new String[mTemplateList.size()];
        intValues = new int[mTemplateList.size()];
        for (ix = 0 ; ix < mTemplateList.size(); ix++) {
            stringPrompts[ix] = mTemplateList.get(ix).title;
            if (stringPrompts[ix].endsWith(" (Template)"))
                stringPrompts[ix] = stringPrompts[ix].substring(0, stringPrompts[ix].length() - 11);
            intValues[ix] = ix;
        }

        mGpUseTemplate = new ActionGroup(ACTIONTYPE_BUTTONS, R.string.sched_use_template,
                stringPrompts, intValues, -1);
        mainActions.add(mGpUseTemplate.mGuidedAction);
        mGroupList.add(mGpUseTemplate);

//    }
//    Uncomment here to add the save and cancel buttons on the right instead of below.
//    @Override
//    public void onCreateButtonActions(@NonNull List<GuidedAction> mainActions, Bundle savedInstanceState) {

        // Save Button
        mGpSaveButton = new ActionGroup(ACTIONTYPE_BUTTON, R.string.sched_save_button);
        mainActions.add(mGpSaveButton.mGuidedAction);
        mGroupList.add(mGpSaveButton);

        // Cancel button
        mGpCancelButton = new ActionGroup(ACTIONTYPE_BUTTON, android.R.string.cancel);
        mainActions.add(mGpCancelButton.mGuidedAction);
        mGroupList.add(mGpCancelButton);
    }

    private void updateRecordRule() {
        mRecordRule.type = mGpType.mStringResult;
        mRecordRule.recGroup = mGpRecGroup.mStringResult;
        mRecordRule.inactive = (mGpActive.mIntResult == 0);
        mRecordRule.playGroup = mGpPlayGroup.mStringResult;
        mRecordRule.startOffset = mGpStartOffset.mIntResult;
        mRecordRule.endOffset = mGpEndOffset.mIntResult;
        mRecordRule.newEpisOnly = (mGpNewEpisOnly.mIntResult != 0);
        mRecordRule.recPriority = mGpRecPriority.mIntResult;
        mRecordRule.preferredInput = mGpPreferredInput.mIntResult;
        mRecordRule.dupMethod = mGpDupMethod.mStringResult;
        mRecordRule.dupIn = mGpDupIn.mStringResult;
        mRecordRule.filter = mGpFilter.mIntResult;
        mRecordRule.recProfile = mGpRecProfile.mStringResult;
        mRecordRule.storageGroup = mGpStorageGroup.mStringResult;
        mRecordRule.maxEpisodes = mGpMaxEpisodes.mIntResult;
        mRecordRule.maxNewest = (mGpMaxNewest.mIntResult != 0);
        mRecordRule.autoExpire = (mGpAutoExpire.mIntResult != 0);
        mRecordRule.autoCommflag = (mGpPostProc.mIntResult & (1 << 0)) != 0;
        mRecordRule.autoMetaLookup = (mGpPostProc.mIntResult & (1 << 1)) != 0;
        mRecordRule.autoTranscode = (mGpPostProc.mIntResult & (1 << 2)) != 0;
        mRecordRule.autoUserJob1 = (mGpPostProc.mIntResult & (1 << 3)) != 0;
        mRecordRule.autoUserJob2 = (mGpPostProc.mIntResult & (1 << 4)) != 0;
        mRecordRule.autoUserJob3 = (mGpPostProc.mIntResult & (1 << 5)) != 0;
        mRecordRule.autoUserJob4 = (mGpPostProc.mIntResult & (1 << 6)) != 0;
        if (mGpInetRefNum.mIntResult > 0) {
            String prefix;
            if (mGpInetRefType.mIntResult == 0)
                prefix = "ttvdb.py_";
            else
                prefix = "tmdb3.py_";
            mRecordRule.inetref = prefix + mGpInetRefNum.mIntResult;
        }
        else
            mRecordRule.inetref = null;

        AsyncBackendCall call = new AsyncBackendCall(this);
        call.setRecordRule(mRecordRule);
        if ("Not Recording". equals(mRecordRule.type)) {
            if (mRecordRule.recordId > 0)
                call.execute(Video.ACTION_DELETERECRULE);
            else
                getActivity().finish();
        }
        else
            call.execute(Video.ACTION_ADD_OR_UPDATERECRULE);
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_ADD_OR_UPDATERECRULE:
            case Video.ACTION_DELETERECRULE:
                XmlNode response = taskRunner.getXmlResult();
                String result = null;
                if (response != null)
                    result = response.getString();
                if (result == null) {
                    Toast.makeText(getContext(),R.string.sched_failed, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(),R.string.sched_updated, Toast.LENGTH_LONG).show();
                    Log.i(TAG, CLASS + " Recording scheduled, Response:" + result);
                    getActivity().finish();
                }
                break;
        }
    }



    private void mergeTemplate(RecordRule template) {

        mIsDirty = true;

        mGpActive.setValue(!template.inactive);
        mGpRecPriority.setValue(template.recPriority);
        mGpPreferredInput.setValue(template.preferredInput);
        mGpStartOffset.setValue(template.startOffset);
        mGpEndOffset.setValue(template.endOffset);
        mGpDupMethod.setValue(template.dupMethod);
        mGpDupIn.setValue((template.dupIn));
        mGpNewEpisOnly.setValue(template.newEpisOnly);
        mGpFilter.setValue(template.filter);
        mGpRecProfile.setValue(template.recProfile);
        mGpRecGroup.setValue(template.recGroup);
        mGpStorageGroup.setValue((template.storageGroup));
        mGpPlayGroup.setValue(template.playGroup);
        mGpAutoExpire.setValue(template.autoExpire);
        mGpMaxEpisodes.setValue(template.maxEpisodes);
        mGpMaxNewest.setValue(template.maxNewest);
        // post Processing
        int ppVal = 0;
        ppVal |= template.autoCommflag   ? 1 << 0 : 0;
        ppVal |= template.autoMetaLookup ? 1 << 1 : 0;
        ppVal |= template.autoTranscode  ? 1 << 2 : 0;
        ppVal |= template.autoUserJob1   ? 1 << 3 : 0;
        ppVal |= template.autoUserJob2   ? 1 << 4 : 0;
        ppVal |= template.autoUserJob3   ? 1 << 5 : 0;
        ppVal |= template.autoUserJob4   ? 1 << 6 : 0;
        mGpPostProc.setValue(ppVal);
        mRecordRule.transcoder = template.transcoder;
    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {
        int id = (int) action.getId();
        int group = id / 100;
        ActionGroup acGrp = mGroupList.get(group);
        boolean ret = acGrp.onSubGuidedActionClicked(action);
        if (acGrp == mGpUseTemplate) {
            RecordRule template = mTemplateList.get(acGrp.mSelectedPrompt);
            mergeTemplate(template);
            acGrp.mGuidedAction.setDescription
                (getContext().getString(R.string.sched_template_applied,
                        acGrp.mStringValues[acGrp.mSelectedPrompt]));
            notifyActionChanged(findActionPositionById(acGrp.mId));
        }
        return ret;
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        int id = (int) action.getId();
        int group = id / 100;
        mGroupList.get(group).onGuidedActionEditedAndProceed(action);
        return GuidedAction.ACTION_ID_CURRENT;
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        int id = (int) action.getId();
        int group = id / 100;
        ActionGroup acGrp = mGroupList.get(group);
        acGrp.onGuidedActionClicked(action);
        super.onGuidedActionClicked(action);
        if (acGrp == mGpSaveButton)
            updateRecordRule();
        else if (acGrp == mGpCancelButton)
            getActivity().finish();;
    }

    private void promptForNewValue(GuidedAction action, String initValue) {
        mNewValueText = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.sched_new_entry);
        EditText input = new EditText(getContext());
        input.setText(initValue);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mNewValueText = input.getText().toString();
                onSubGuidedActionClicked(action);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    boolean canEnd() {
        // No save needed if deleting a non-existing rule
        if (mRecordRule.recordId == 0 && "Not Recording".equals(mGpType.mStringResult))
            return true;
        if (mIsDirty) {
            // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                    R.style.Theme_AppCompat_Dialog_Alert);
            builder
                    .setTitle(R.string.menu_changes)
                    .setItems(R.array.prompt_save_changes,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // The 'which' argument contains the index position
                                    // of the selected item
                                    // 0 = save, 1 = continue, 2 = exit
                                    switch (which) {
                                        case 0:
                                            updateRecordRule();
                                            break;
                                        case 2:
                                            getActivity().finish();
                                            break;
                                    }
                                }
                            });
            builder.show();
            return false;
        }
        return true;
    }

    // Handles groups of checkboxes or radiobuttons.
    private class ActionGroup {
        int mActionType;
        int mTitle;
        int[] mPrompts;          // can be null in which case mStringValues are used
        int[] mIntValues;        // can be null
        String[] mStringValues;  // can be null
        int mId;                  // id of main action. sub actions have sequential ids after this.
        int mSubActionCount;
        int mIntResult = -1;
        String mStringResult;
        boolean mEditLast;
        int mSelectedPrompt = -1;
        GuidedAction mGuidedAction;

        ActionGroup(int actionType, int title, int[] prompts, int[] intValues,
                    String[] stringValues, String currStringValue, int currIntValue, boolean editLast) {
            mActionType = actionType;
            mIntValues = intValues;
            mStringValues = stringValues;
            mPrompts = prompts;
            mIntResult = currIntValue;
            mStringResult = currStringValue;
            if (mStringValues != null)
                mEditLast = editLast;
            mId = mGroupId++ * 100 + 1;
            int subId = mId;

            if (intValues != null)
                mSubActionCount = intValues.length;
            else if (stringValues != null)
                mSubActionCount = stringValues.length;
            else if (mPrompts != null && actionType != ACTIONTYPE_BOOLEAN)
                mSubActionCount = mPrompts.length;
            else
                mSubActionCount = 0;

            List<GuidedAction> subActions = new ArrayList<>();
            for (int ix = 0; ix < mSubActionCount; ix++) {
                GuidedAction.Builder builder = new GuidedAction.Builder(getActivity())
                        .id(++subId);
                if (mEditLast && ix == mSubActionCount-1) {
                    builder.title(R.string.sched_new_entry);
                }
                else if (mPrompts != null)
                    builder.title(mPrompts[ix]);
                else if (mStringValues != null)
                    builder.title(mStringValues[ix]);
                boolean checked = false;
                if (mActionType == ACTIONTYPE_CHECKBOXES)
                    checked = ((mIntResult & (1 << ix)) != 0);
                else {
                    if (currStringValue != null)
                        checked = (currStringValue.equals(mStringValues[ix]));
                    else
                        checked = (currIntValue == mIntValues[ix]);
                    if (checked) {
                        if (mPrompts != null)
                            mSelectedPrompt = ix;
                        if (mStringValues != null)
                            mStringResult = mStringValues[ix];
                        if (mIntValues != null)
                            mIntResult = mIntValues[ix];
                    }
                }
                switch (mActionType) {
                    case ACTIONTYPE_CHECKBOXES:
                        builder.checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID);
                        break;
                    case ACTIONTYPE_RADIOBNS:
                        builder.checkSetId(mId);
                        break;
                }
                builder.checked(checked);
                subActions.add(builder.build());
            }
            GuidedAction.Builder builder = new GuidedAction.Builder(getActivity())
                    .id(mId)
                    .title(title);
            if (mActionType == ACTIONTYPE_RADIOBNS) {
                if (mSelectedPrompt >= 0)
                    builder.description(mPrompts[mSelectedPrompt]);
                else if (mStringResult != null)
                    builder.description(mStringResult);
            }
            else if (mActionType == ACTIONTYPE_NUMERIC
                    || mActionType == ACTIONTYPE_NUMERIC_UNSIGNED) {
                builder.description(String.valueOf(mIntResult));
                builder.descriptionEditable(true);
                int type = InputType.TYPE_CLASS_NUMBER;
                if (mActionType == ACTIONTYPE_NUMERIC)
                    type |= InputType.TYPE_NUMBER_FLAG_SIGNED;
                builder.descriptionEditInputType (type);
            }
            else if (mActionType == ACTIONTYPE_BOOLEAN) {
                builder.checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID);
                builder.checked(mIntResult != 0);
//                builder.description(getContext().getString(mPrompts[mIntResult]));
                builder.description(mPrompts[mIntResult]);
            }
            if (mSubActionCount > 0)
                builder.subActions(subActions);
            mGuidedAction = builder.build();
        }

        public void setValue(int intValue) {
            List<GuidedAction> subActionList;
            GuidedAction action;
            switch(mActionType) {
                case ACTIONTYPE_RADIOBNS:
                    // mIntValues must be filled in
                    if (mIntValues == null)
                        break;
                    mSelectedPrompt = -1;
                    for (int ix = 0; ix < mIntValues.length; ix++) {
                        if (intValue == mIntValues[ix]) {
                            mSelectedPrompt = ix;
                            break;
                        }
                    }
                    if (mSelectedPrompt == -1)
                        break;
                    subActionList = mGuidedAction.getSubActions();
                    for (GuidedAction a : subActionList)
                        a.setChecked(false);
                    action = subActionList.get(mSelectedPrompt);
                    action.setChecked(true);
                    mIntResult = intValue;
                    if (mStringValues != null)
                        mStringResult = mStringValues[mSelectedPrompt];
                    if (mPrompts != null)
                        mGuidedAction.setDescription(getContext().getString(mPrompts[mSelectedPrompt]));
                    else
                        mGuidedAction.setDescription(mStringResult);
                    notifyActionChanged(findActionPositionById(mGuidedAction.getId()));
                    break;
                case ACTIONTYPE_BOOLEAN:
                    mIntResult = intValue;
                    mGuidedAction.setChecked(mIntResult != 0);
                    mGuidedAction.setDescription(getContext().getString(mPrompts[mIntResult]));
                    notifyActionChanged(findActionPositionById(mGuidedAction.getId()));
                    break;
                case ACTIONTYPE_NUMERIC:
                case ACTIONTYPE_NUMERIC_UNSIGNED:
                    mIntResult = intValue;
                    mGuidedAction.setDescription(String.valueOf(mIntResult));
                    notifyActionChanged(findActionPositionById(mGuidedAction.getId()));
                    break;
                case ACTIONTYPE_CHECKBOXES:
                    mIntResult = intValue;
                    subActionList = mGuidedAction.getSubActions();
                    for (int ix = 0; ix < subActionList.size(); ix++) {
                        boolean checked = ((mIntResult & (1 << ix)) != 0);
                        GuidedAction subAction = subActionList.get(ix);
                        subAction.setChecked(checked);
                    }
            }
        }

        public void setValue(String strValue) {
            switch(mActionType) {
                case ACTIONTYPE_RADIOBNS:
                    // mStringValues must be filled in
                    if (mStringValues == null)
                        break;
                    mSelectedPrompt = -1;
                    for (int ix = 0; ix < mStringValues.length; ix++) {
                        if (Objects.equals(strValue, mStringValues[ix])) {
                            mSelectedPrompt = ix;
                            break;
                        }
                    }
                    if (mSelectedPrompt == -1)
                        break;
                    List<GuidedAction> subActionList = mGuidedAction.getSubActions();
                    for (GuidedAction action : subActionList)
                        action.setChecked(false);
                    GuidedAction action = subActionList.get(mSelectedPrompt);
                    action.setChecked(true);
                    mStringResult = strValue;
                    if (mPrompts != null)
                        mGuidedAction.setDescription(getContext().getString(mPrompts[mSelectedPrompt]));
                    else
                        mGuidedAction.setDescription(mStringResult);
                    notifyActionChanged(findActionPositionById(mGuidedAction.getId()));
                    break;
            }

        }

        public void setValue(boolean bValue) {
            setValue(bValue ? 1 : 0);
        }

        /**
         * Constructor for one numeric input value
         * @param actionType   ACTIONTYPE_NUMERIC or ACTIONTYPE_NUMERIC_UNSIGNED
         * @param title        title
         * @param currIntValue initial value
         */
        ActionGroup(int actionType, int title, int currIntValue) {
            this(actionType, title, null, null,
                    null, null, currIntValue, false);
        }


        /**
         * Constructor for list of integer values with string prompts
         * @param actionType   ACTIONTYPE_RADIOBNS
         * @param title        title
         * @param prompts      array: string prompt for each value. Null for numeric input.
         * @param intValues    array: Int values. Null for numeric input.
         * @param currIntValue initial value
         */
        ActionGroup(int actionType, int title, @NonNull String [] prompts, @NonNull int[] intValues,
                    int currIntValue) {
            this(actionType, title, null, intValues,
                    prompts, null, currIntValue, false);
        }


        /**
         * Constructor for list of string values with string id prompts.
         * @param actionType       ACTIONTYPE_RADIOBNS
         * @param title            title
         * @param prompts          array of prompts if descriptions diff from value. null to use
         *                         values as prompts
         * @param stringValues     array of values
         * @param currStringValue  initial value
         * @param allowCreateNew   add an option to create a new value
         */
        ActionGroup(int actionType, int title, int[] prompts,
                    @NonNull  String[] stringValues, String currStringValue, boolean allowCreateNew) {
            this(actionType, title, prompts, null,
                    stringValues, currStringValue, -1, allowCreateNew);
        }

        /**
         * Constructor for check boxes with string or sting id prompts
         * @param actionType    ACTIONTYPE_CHECKBOXES
         * @param title         title
         * @param prompts       array: string id prompts. null to use string values.
         * @param stringPrompts array: String prompts. Null to use string id's.
         * @param currIntValue  initial value (bit mask)
         */
        ActionGroup(int actionType, int title, int[] prompts, String[] stringPrompts,
                    int currIntValue) {
            this(actionType, title, prompts, null,
                    stringPrompts, null, currIntValue, false);
        }

        /**
         * Constructor for boolean prompt
         * @param actionType     ACTIONTYPE_BOOLEAN
         * @param title          title
         * @param prompts        array of two entries, for false and true
         * @param currBoolValue  initial value
         */
        ActionGroup(int actionType, int title, int [] prompts, boolean currBoolValue) {
            this(actionType, title, prompts, null,
                    null, null,
                    currBoolValue ? 1 : 0, false);
        }

        /**
         * Constructor for save button
         * @param actionType     ACTIONTYPE_BUTTON
         * @param title          title
         */
        ActionGroup(int actionType, int title) {
            this(actionType, title, null, null,
                    null, null,
                    -1, false);
        }

        public boolean onSubGuidedActionClicked(GuidedAction action) {
            int id = (int) action.getId();
            int ix = (id % 100) - 2;
            mSelectedPrompt = ix;
            mIsDirty = true;
            if (action.isChecked()) {
                if (mActionType == ACTIONTYPE_CHECKBOXES) {
                    mIntResult |= (1 << ix);
                    return false;
                }
                else if (mActionType == ACTIONTYPE_RADIOBNS
                        || mActionType == ACTIONTYPE_BUTTONS) {
                    if (mEditLast && ix == mStringValues.length-1) {
                        if (mNewValueText == null)
                            promptForNewValue(action, mStringValues[mStringValues.length - 1]);
                        else
                            mStringValues[mStringValues.length-1] = mNewValueText;
                        mNewValueText = null;
                        action.setDescription(mStringValues[mStringValues.length-1]);
                    }

                    if (mIntValues != null)
                        mIntResult = mIntValues[ix];
                    if (mStringValues != null)
                        mStringResult = mStringValues[ix];
                    if (mPrompts != null) {
                        String s = getContext().getString(mPrompts[ix]);
                        mGuidedAction.setDescription(s);
                        if (mStringValues == null)
                            mStringResult = s;
                    } else if (mStringValues != null) {
                        mGuidedAction.setDescription(mStringResult);
                    }
                    notifyActionChanged(findActionPositionById(mId));
                }
            }
            else {
                if (mActionType == ACTIONTYPE_CHECKBOXES) {
                    mIntResult &= (-1 - (1 << ix));
                    return false;
                }

            }
            return true;
        }

        public void onGuidedActionEditedAndProceed(GuidedAction action) {
            if (mActionType == ACTIONTYPE_NUMERIC
                || mActionType == ACTIONTYPE_NUMERIC_UNSIGNED) {
                mIsDirty = true;
                try {
                    mIntResult = Integer.parseInt(action.getDescription().toString().trim());
                }
                catch(Exception e) {
                    mIntResult = 0;
                }
                action.setDescription(String.valueOf(mIntResult));
                notifyActionChanged(findActionPositionById(action.getId()));
            }
        }

        public void onGuidedActionClicked(GuidedAction action) {
            if (mActionType == ACTIONTYPE_BOOLEAN) {
                mIsDirty = true;
                mIntResult = action.isChecked() ? 1 : 0;
                action.setDescription(getContext().getString(mPrompts[mIntResult]));
                notifyActionChanged(findActionPositionById(action.getId()));
            }
        }
    }
}