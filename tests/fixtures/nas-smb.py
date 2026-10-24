"""Local synthetic SMB2 server. No private device or account is used."""
import logging
import sys
import os
from impacket.smbserver import SimpleSMBServer
from impacket.ntlm import compute_lmhash, compute_nthash
logging.basicConfig(level=logging.DEBUG if os.environ.get("APERTURE_NAS_TEST_TRACE")=="1" else logging.CRITICAL)
server = SimpleSMBServer(listenAddress="127.0.0.1", listenPort=int(sys.argv[1]))
server.setSMB2Support(True)
server.addShare("Family", sys.argv[2], readOnly="yes")
server.addCredential("fixture", 0, compute_lmhash("synthetic-nas-only"), compute_nthash("synthetic-nas-only"))
print("READY", flush=True)
server.start()
