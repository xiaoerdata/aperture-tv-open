export function editions(args, env = process.env) {
  let selected = env.APERTURE_ANDROID_EDITION || 'standard';
  if (args.length) {
    if (args.length !== 2 || args[0] !== '--edition') throw new Error('Use --edition standard, parents or both');
    selected = args[1];
  }
  if (!['standard', 'parents', 'both'].includes(selected)) throw new Error('Unknown Android edition');
  return selected === 'both' ? ['standard', 'parents'] : [selected];
}
