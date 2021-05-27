package examples.cdmdatasets;

import ucar.ma2.ArrayStructureBB;
import ucar.ma2.StructureMembers;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.internal.iosp.netcdf3.N3header;

public class StructureDataArraysTutorial {

    public static void createArrayStructure (Structure s){
        // create the ArrayStructure
        /*ucar.ma2.StructureMembers members = s.makeStructureMembers();
        for (StructureMembers.Member m : members.getMembers()) {
            Variable v2 = s.findVariable(m.getName());
            N3header.Vinfo vinfo = (N3header.Vinfo) v2.getSPobject();
            m.setDataParam((int) (vinfo.begin - recStart)); // the offset from the start of the record
        }
        members.setStructureSize(recsize);  // the size of each record is constant

        // create the ArrayStructureBB
        ArrayStructureBB structureArray = new ArrayStructureBB(members, new int[]{recordRange.length()});
        byte[] result = structureArray.getByteBuffer().array();

        // read the data one record at a time into the ByteBuffer
        int count = 0;
        for (int recnum = recordRange.first(); recnum <= recordRange.last(); recnum += recordRange.stride()) {
            raf.seek(recStart + recnum * recsize); // where the record starts
            raf.readFully(result, count * recsize, recsize);
            count++;
        }*/

    }
}
