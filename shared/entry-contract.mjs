// Shared machine contract and domain required fields. Human and Agent writes use the same model.
export const entryRequired={note:['type','title','body','author'],event:['type','title','date','time','person'],metric:['type','title','value','target','unit','source','date']};
const string=(maxLength)=>({type:'string',minLength:1,maxLength});
const common={title:string(48)};
const date={type:'string',pattern:'^\\d{4}-\\d{2}-\\d{2}$',description:'YYYY-MM-DD; must be a real calendar date'};
export const entrySchema={oneOf:[
 {type:'object',required:entryRequired.note,additionalProperties:false,properties:{type:{const:'note'},...common,body:string(180),author:string(16),pinned:{type:'boolean'}}},
 {type:'object',required:entryRequired.event,additionalProperties:false,properties:{type:{const:'event'},...common,date,time:{type:'string',pattern:'^([01]\\d|2[0-3]):[0-5]\\d$'},person:string(16),done:{type:'boolean'}}},
 {type:'object',required:entryRequired.metric,additionalProperties:false,properties:{type:{const:'metric'},...common,value:{type:'number',minimum:0,maximum:1e9},target:{type:'number',exclusiveMinimum:0,maximum:1e9},unit:string(8),source:string(40),date}}
]};
