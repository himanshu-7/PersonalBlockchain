package nl.tudelft.cs4160.trustchain_android.database;

import java.lang.reflect.Array;
import java.util.ArrayList;

//Here are described all the possibile type of infomrmation that is possible to store in a blockchain

public class Types {
    ArrayList<Type> types = new ArrayList<Type>();

    public Types() {
        types.add(new Type(1, "Name", false));
        types.add(new Type(2, "Surname", false));
        types.add(new Type(3, "Date Of Birth", false));
        types.add(new Type(4, "Age", true));
        types.add(new Type(5, "Sex", false));
        types.add(new Type(6, "TestID", true));
        types.add(new Type(7, "Bank Details", false));
        types.add(new Type(8, "Social Number", false));

    }

    public ArrayList<Type> getTypes() {
        return types;
    }

    public ArrayList<String> getTypesNormal(ArrayList<BlockDescription> blockTypeToIgnore) {
        ArrayList<String> typesFiltered = new ArrayList<String>();
        for (Type type : types) {

            if (type.ZKPCompatible == false && !isInsideTheList(type.typeID, blockTypeToIgnore)) {
                typesFiltered.add(type.description);
            }
        }
        return typesFiltered;
    }


    public ArrayList<String> getTypesZKP(ArrayList<BlockDescription> blockTypeToIgnore) {
        ArrayList<String> typesFiltered = new ArrayList<String>();
        for (Type type : types) {

            if (type.ZKPCompatible == true && !isInsideTheList(type.typeID, blockTypeToIgnore)) {
                typesFiltered.add(type.description);
            }
        }
        return typesFiltered;
    }

    private boolean isInsideTheList(int id, ArrayList<BlockDescription> listOfBlockDescription) {
        if (listOfBlockDescription == null)
            return false;

        for (BlockDescription thisBlockDesc : listOfBlockDescription) {
            if (thisBlockDesc.typeID == id) {
                return true;
            }
        }
        return false;
    }

    public int findTypeIDByDescription(String desc) {
        for (Type type : types) {

            if (type.description.compareTo(desc) == 0) {
                return type.typeID;
            }
        }
        return -1;
    }

    public String findDescriptionByTypeID(int ID) {
        for (Type type : types) {

            if (type.typeID == ID) {
                return type.description;
            }
        }
        return null;
    }

    public boolean isZkpCompatible(int ID){
        for(Type type: types){
            if(type.typeID == ID) {
                return type.ZKPCompatible;
            }
        }
        return false;
    }

}


class Type {
    public int typeID;
    public String description;
    public Boolean ZKPCompatible;

    Type(int typeID, String desctiption, boolean ZKPCompatible) {
        this.typeID = typeID;
        this.description = desctiption;
        this.ZKPCompatible = ZKPCompatible;
    }

}
