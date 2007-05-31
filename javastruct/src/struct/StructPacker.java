package struct;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Vector;

public class StructPacker extends StructOutputStream{
	protected ByteArrayOutputStream bos;
	private Object objectToPack = null;

    public StructPacker(Object objectToPack){
        this(objectToPack,true);
    }

    public StructPacker(Object objectToPack, boolean isLittleEndian){
    	this.objectToPack = objectToPack;
		bos = new ByteArrayOutputStream();
		super.init(bos, isLittleEndian);
		super.modifiers = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED ;
    }

	public StructPacker(Object objectToPack, OutputStream os){
        this.objectToPack = objectToPack ;
        bos = (ByteArrayOutputStream)os;
        super.init(bos, true);
	}

    public byte[] pack() throws StructException {
        writeObject(objectToPack);
        return bos.toByteArray();
    }

    public static byte[] packBigEndian(Object objectToPack) throws StructException {
        StructPacker packer = new StructPacker(objectToPack, false);
        return packer.pack();
    }
    
    public static byte[] pack(Object objectToPack) throws StructException {
        StructPacker packer = new StructPacker(objectToPack);
        return packer.pack();
    }

	public static byte[] asByteArray(Object objectToPack) throws RuntimeException {
		try {
			StructPacker packer = new StructPacker(objectToPack);
			return packer.pack();
		} catch (StructException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Serialize Object as a struct
	 */
	public void writeObject( Object obj) throws StructException{
        if(obj == null)
        	throw new StructException("Struct classes cant be null.");
        StructUtils.implementsStruct(obj);
        StructUtils.isAccessible(obj);
        StructData info = StructUtils.getStructInfo(obj);
        Field[] fields = info.getFields();

		Field currentField = null;
        Vector<Field> lengthedArrayFields = new Vector<Field>();
        Vector<Object> lengthedArrayFieldValues = new Vector<Object>();
        boolean lengthedArray = false;
        boolean lengthedArrayField = false;
        int arrayLength = 0;

		for ( int i=0; i<fields.length; i++) {
            lengthedArray = false; 
            lengthedArrayField = false; 
            arrayLength = 0;
			currentField = fields[i];
            try{
    			// Is field appropriate for operations? 
                // Legal modifier masks: public, private, protected
                if (isFieldValid(currentField.getModifiers())){
                    if(info.isLenghtedArray(currentField)){
                        lengthedArrayFields.add(currentField);
                        lengthedArrayField = true;
                    }
                    else {
                        if(currentField.getType().isArray()) {
                            Object temp = StructUtils.isLengthtedArray( obj, lengthedArrayFields, lengthedArrayFieldValues, currentField);
                            if(temp != null){
                                arrayLength = ((Number)temp).intValue();
                                lengthedArray = true;
                            }
                        }
                    }
                    if ( StructUtils.requiresGetterSetter(currentField.getModifiers()) ){
                        Method getter = info.getGetter(currentField);
                        if(getter != null){
                            if (lengthedArrayField)
                                lengthedArrayFieldValues.add(getter.invoke( obj, (Object[])null));
                            if(lengthedArray && arrayLength >= 0){
                                writeField(currentField, getter, obj, arrayLength);
                            }
                            else writeField(currentField, getter,obj, -1);
                        }
                        else{
                            throw new StructException("Field requires a getter : "+ currentField.getName());
                        }
                    }
                    // Field is public. Access directly.
                    else {
                        if (lengthedArrayField)
                            lengthedArrayFieldValues.add(currentField.get(obj));
                        if(lengthedArray){
                            // Array is null if Length is negative.
                            if(arrayLength >= 0){
                                writeField(currentField, null, obj, arrayLength);
                            }
                        }
                        else {
                            writeField(currentField, null, obj, -1);
                        }
                    }
                }
            }
            catch (Exception e) {
            	throw new StructException(e);
            }
		}
	}

    public boolean isFieldValid(int modifier){
        return (modifier == 0) || ((modifier & ~modifiers) == 0 && (modifier | modifiers) != 0);
    }
}
