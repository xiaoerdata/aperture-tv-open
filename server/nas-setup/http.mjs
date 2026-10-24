import QRCode from 'qrcode';
import {fail} from '../../shared/model.mjs';

export async function nasSetupHttp({path,req,actor,body,service,origin}) {
  if(path==='/api/nas-setup/start'&&req.method==='POST'){
    const result=service.start(actor,await body());
    const approvalUrl=origin+'/nas-setup?id='+result.id+'#fingerprint='+result.fingerprint;
    const qr=(await QRCode.toBuffer(approvalUrl,{width:360,margin:2,errorCorrectionLevel:'M'})).toString('base64');
    return {...result,approvalUrl,qr};
  }
  if(path==='/api/nas-setup/requests'&&req.method==='GET')return service.list(actor);
  if(path==='/api/nas-setup/inspect'&&req.method==='POST'){const b=await body();return service.inspect(actor,b.id,b.key);}
  if(path==='/api/nas-setup/send'&&req.method==='POST')return service.send(actor,await body());
  if(path==='/api/nas-setup/poll'&&req.method==='POST')return service.poll(actor,await body());
  if(path==='/api/nas-setup/finish'&&req.method==='POST')return service.finish(actor,await body());
  fail('接口不存在',404,'not_found');
}
