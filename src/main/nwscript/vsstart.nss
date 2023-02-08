#include "vaultsync_inc"

// Make sure the character's bic file is saved before calling this script
// otherwise they may send an out of date copy.
//
// If you're using "vsstart" and "vsevent" you must set the following
// localstrings on the PC:
//
// VS_PEER - The name of the vaultsync peer as defined in the local vaultsync
//           server's application.yml
// VS_DEST_ADDR - The sIPaddress argument for ActivatePortal
// VS_DEST_PASS - The sPassword argmuent for ActivatePort (optional)
// VS_DEST_WP   - The sWaypointTag argument for ActivatePortal
//
// WARNING: The call to ExportSingleCharacter() returns before the bic has been
//   saved.  You must give it enough time to write out the file.  Very full
//   inventories have been known to take several seconds.
//
// Usage:
//   SetLocalString(oPC,"VS_PEER", sPeer);
//   SetLocalString(oPC,"VS_DEST_ADDR", sAddr);
//   SetLocalString(oPC,"VS_DEST_WP", sWP);
//
//   SendMessageToPC(oPC,"Exporting character...");
//   ExportSingleCharacter(oPC);
//   DelayCommand(5.0f,ExecuteScript("vsstart",oPC));

void main()
{
    string sDest = GetLocalString(OBJECT_SELF,"VS_PEER");
    struct RequestStatus s = VaultSyncStart(OBJECT_SELF, sDest);
    if(s.statusCode > 299) {
        SendMessageToPC(OBJECT_SELF,"Could not start transfer.  Code: "+IntToString(s.statusCode)+" Error: "+s.errorMessage);
    } else {
        SendMessageToPC(OBJECT_SELF,"Starting transfer.");
        DelayCommand(1.0f, ExecuteScript("vsevent"));
    }
}
