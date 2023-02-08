#include "vaultsync_inc"

void cleanup()
{
    DeleteLocalString(OBJECT_SELF, "VS_PEER");
    DeleteLocalString(OBJECT_SELF, "VS_DEST_ADDR");
    DeleteLocalString(OBJECT_SELF, "VS_DEST_PASS");
    DeleteLocalString(OBJECT_SELF, "VS_DEST_WP");
}

void main()
{
    int nRetries = GetLocalInt(OBJECT_SELF, "VS_COUNT")+1;
    if(nRetries > 10) {
        SendMessageToPC(OBJECT_SELF,"Unable to complete transfer. Please contact the staff.");
        cleanup();
        return;
    }

    SetLocalInt(OBJECT_SELF, "VS_COUNT", nRetries);

    string sDestination = GetLocalString(OBJECT_SELF, "VS_PEER");
    struct TransferStatus s = VaultSyncStatus(OBJECT_SELF, sDestination);

    if(s.statusCode > 299) {
        SendMessageToPC(OBJECT_SELF,"Could not complete transfer.  Code: "+IntToString(s.statusCode)+" Error: "+s.errorMessage);
        cleanup();
    } else
    if(s.statusCode == 200 && s.progress == 1000) {
        SendMessageToPC(OBJECT_SELF,"Transfer complete.  Portalling in 5 seconds");
        string sAddr = GetLocalString(OBJECT_SELF, "VS_DEST_ADDR");
        string sPass = GetLocalString(OBJECT_SELF, "VS_DEST_PASS");
        string sWaypoint = GetLocalString(OBJECT_SELF, "VS_DEST_WP");
        cleanup();
        DelayCommand(5.0f, ActivatePortal(OBJECT_SELF, sAddr, sPass, sWaypoint, TRUE));
    } else {
        SendMessageToPC(OBJECT_SELF,"Transfer progress: "+IntToString(s.progress));
        DelayCommand(1.0f, ExecuteScript("vsevent"));
    }
}
