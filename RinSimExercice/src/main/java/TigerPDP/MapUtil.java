package TigerPDP;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.rinde.rinsim.geom.Point;

public class MapUtil
{
    public static <K, V extends Comparable<? super V>> Map<K, V> 
        sortByValue( Map<K, V> map )
    {
        List<Map.Entry<K, V>> list =
            new LinkedList<Map.Entry<K, V>>( map.entrySet() );
        Collections.sort( list, new Comparator<Map.Entry<K, V>>()
        {
            public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
            {
                return (o1.getValue()).compareTo( o2.getValue() );
            }
        } );

        Map<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list)
        {
            result.put( entry.getKey(), entry.getValue() );
        }
        return result;
    }
    
    public static Point addPoints(Point p1, Point p2) {
    	return new Point(p1.x + p2.x, p1.y + p2.y);
    }
    
    public static double pointNorm(Point p) {
    	return Math.sqrt(Math.pow(p.x,2) + Math.pow(p.y,2));
    }
    
    public static Point rescale(Point p, double scale) {
    	return new Point(p.x*scale, p.y*scale);
    }
    
    public static Point normalize(Point p) {
    	return rescale(p, 1/pointNorm(p));
    }
    
   
}
