#include "restcall_inc"
#include "nwnx_player"

struct RequestStatus
{
    int statusCode;
    string errorMessage;
};

struct TransferStatus
{
    int progress;
    int statusCode;
    string errorMessage;
};

string vsGetServer()
{
    object oModule = GetModule();
    string sServer = GetLocalString(oModule,"VS_SERVER");
    if(sServer == "") sServer = "vaultsync";
    string sPort = GetLocalString(oModule,"VS_PORT");
    if(sPort == "") sPort = "3022";
    return sServer + ":" + sPort;
}

// Calls the VaultSync server to copy the PC's bic to the destination server
// - oPC: the character that will be transferred
// - sDestination: where the PC will be sent.  This must be a defined peer in
//         VaultSync's application.yml file.
// - returns:
//        struct RequestStatus
//        {
//           int statusCode;
//           string errorMessage;
//        };
//   - statusCode: 0-199: In progress, 200-299: Success, 300+ error
//   - errrorMessage: "" or the description of the error
struct RequestStatus VaultSyncStart(object oPC, string sDestination);
struct RequestStatus VaultSyncStart(object oPC, string sDestination)
{
    struct RequestStatus results;
    if(!GetIsPC(oPC) || GetIsDM(oPC)) {
        results.statusCode = 400;
        results.errorMessage = "May only transfer non-DM player characters";
        return results;
    }

    json jTransferData = JsonObject();
    jTransferData = JsonObjectSet(jTransferData, "characterName", JsonString(GetName(oPC)));
    jTransferData = JsonObjectSet(jTransferData, "loginName", JsonString(GetPCPlayerName(oPC)));
    jTransferData = JsonObjectSet(jTransferData, "cdkey", JsonString(GetPCPublicCDKey(oPC)));
    jTransferData = JsonObjectSet(jTransferData, "fileName", JsonString(NWNX_Player_GetBicFileName(oPC)));
    jTransferData = JsonObjectSet(jTransferData, "destination", JsonString(sDestination));

    struct JsonResponse jResp = RestCall("POST","https://"+vsGetServer()+"/v1/transfer/start",jTransferData);

    // Did the https transaction fail?
    if(jResp.code == -1) {
        results.statusCode = 500;
        results.errorMessage = jResp.error;
    } else {
        results.statusCode = JsonGetInt(JsonObjectGet(jResp.response,"statusCode"));
        results.errorMessage = JsonGetString(JsonObjectGet(jResp.response,"errorMessage"));
    }

    return results;
}

// Calls the VaultSync server to get the current progress or status of the transfer
// - oPC: the character that being transferred
// - sDestination: where the PC is being sent.  This must match the peer name
//        used in VaultSyncStart()
// - returns:
//        struct TransferStatus
//        {
//           int progress;
//           int statusCode;
//           string errorMessage;
//        };
//   - progress: 0-1000: The scaled percentage of the data transferred
//        Divide by 10 to get 0.0-100.0%
//   - statusCode: 0-199: In progress, 200-299: Success, 300+ error
//   - errrorMessage: "" or the description of the error
struct TransferStatus VaultSyncStatus(object oPC, string destination);
struct TransferStatus VaultSyncStatus(object oPC, string destination)
{
    struct TransferStatus results;
    if(!GetIsPC(oPC) || GetIsDM(oPC)) {
        results.statusCode = 400;
        results.errorMessage = "Not a non-DM player character";
        return results;
    }

    json jBody = JsonObject();
    jBody = JsonObjectSet(jBody, "characterName", JsonString(GetName(oPC)));
    jBody = JsonObjectSet(jBody, "cdkey", JsonString(GetPCPublicCDKey(oPC)));
    jBody = JsonObjectSet(jBody, "destination", JsonString(destination));

    struct JsonResponse jResp = RestCall("POST","https://"+vsGetServer()+"/v1/transfer/status",jBody);

    // Did the https transaction fail?
    if(jResp.code == -1) {
        results.statusCode = 500;
        results.errorMessage = jResp.error;
    } else {
        results.progress = JsonGetInt(JsonObjectGet(jResp.response,"progress"));
        results.statusCode = JsonGetInt(JsonObjectGet(jResp.response,"statusCode"));
        results.errorMessage = JsonGetString(JsonObjectGet(jResp.response,"errorMessage"));
    }

    return results;
}

// Calls the VaultSync server to cancel the current transfer
//        Caveat: If the transfer has completed, the peer will have already replaced the bic.
// - oPC: the character that being transferred
// - sDestination: where the PC is being sent.  This must match the peer name
//        used in VaultSyncStart()
// - returns:
//        struct RequestStatus
//        {
//           int statusCode;
//           string errorMessage;
//        };
//   - statusCode: 0-199: In progress, 200-299: Success, 300+ error
//   - errrorMessage: "" or the description of the error
//struct RequestStatus VaultSyncAbort(object oPC, string destination);
struct RequestStatus VaultSyncAbort(object oPC, string destination)
{
    struct RequestStatus results;
    if(!GetIsPC(oPC) || GetIsDM(oPC)) {
        results.statusCode = 400;
        results.errorMessage = "Not a non-DM player character";
        return results;
    }

    json jBody = JsonObject();
    jBody = JsonObjectSet(jBody, "characterName", JsonString(GetName(oPC)));
    jBody = JsonObjectSet(jBody, "cdkey", JsonString(GetPCPublicCDKey(oPC)));
    jBody = JsonObjectSet(jBody, "destination", JsonString(destination));

    struct JsonResponse jResp = RestCall("POST","https://"+vsGetServer()+"/v1/transfer/abort",jBody);

    // Did the https transaction fail?
    if(jResp.code == -1) {
        results.statusCode = 500;
        results.errorMessage = jResp.error;
    } else {
        results.statusCode = JsonGetInt(JsonObjectGet(jResp.response,"statusCode"));
        results.errorMessage = JsonGetString(JsonObjectGet(jResp.response,"errorMessage"));
    }

    return results;
}

