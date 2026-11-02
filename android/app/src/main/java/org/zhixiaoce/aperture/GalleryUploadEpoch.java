package org.zhixiaoce.aperture;

/** Revocable upload authorization. Resume never revives a revoked request; end is permanent. */
final class GalleryUploadEpoch {
    static final class Permit {final Object owner;final long generation;private boolean started;private Permit(Object owner,long generation){this.owner=owner;this.generation=generation;}}
    private Object owner;private long generation;private boolean active,ended;private Permit current;
    synchronized void page(Object next){revoke();owner=next;}
    synchronized void active(boolean value){if(!value)revoke();active=value&&!ended;}
    synchronized void revoke(){current=null;generation++;}
    synchronized void end(){ended=true;active=false;owner=null;revoke();}
    synchronized Permit accept(Object expected){if(ended||!active||owner==null||owner!=expected)return null;revoke();return current=new Permit(owner,generation);}
    synchronized boolean valid(Permit permit){return permit!=null&&current==permit&&!ended&&active&&owner==permit.owner&&generation==permit.generation;}
    /** Linearization point for starting work; a claimed in-flight operation is closed on revoke. */
    synchronized boolean claim(Permit permit){if(!valid(permit)||permit.started)return false;permit.started=true;return true;}
}
