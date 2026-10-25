import {readFileSync} from 'node:fs';

export const androidAbiPolicy=JSON.parse(readFileSync(new URL('../android/abi-policy.json',import.meta.url),'utf8'));
export function androidAbis(value=process.env.APERTURE_ANDROID_ABIS){
 const selected=value===undefined?[...androidAbiPolicy.default]:value.split(',').map(item=>item.trim());
 if(!selected.length||new Set(selected).size!==selected.length||selected.some(abi=>!androidAbiPolicy.supported.includes(abi)))throw new Error('APERTURE_ANDROID_ABIS must be a nonempty, unique comma-separated list of '+androidAbiPolicy.supported.join(', '));
 return selected;
}
