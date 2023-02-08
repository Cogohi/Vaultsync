void main()
{
    string sDest = "example";
    string sAddr = "nwn.example.com:5125";
    // string sPass = "password";
    string sWP = "WP_ServerPortal1";

    object oPC = GetPCChatSpeaker();
    SetLocalString(oPC,"VS_PEER", sDest);
    SetLocalString(oPC,"VS_DEST_ADDR", sAddr);
    // SetLocalString(oPC,"VS_DEST_PASS", sPass);
    SetLocalString(oPC,"VS_DEST_WP", sWP);

    // Make sure the bic is up to date
    // NOTE: The sending script should make sure that the bic is done being written.
    SendMessageToPC(oPC, "Exporting Character ...");
    ExportSingleCharacter(oPC);

    DelayCommand(5.0f, ExecuteScript("vsstart"));
}
