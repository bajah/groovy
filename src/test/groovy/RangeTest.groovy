class RangeTest extends GroovyTestCase {
	
	void testRange() {
	    x = 0

	    for ( i in 0..9 ) {
	        x = x + i
	    }

	    assert x == 45
	    
	    x = 0

	    for ( i in 0...10 ) {
	        x = x + i
	    }

	    assert x == 45
	}

	void testRangeEach() {
	    x = 0

	    (0..9).each {
	        x = x + it
	    }

	    assert x == 45
	    
	    x = 0

	    (0...10).each {
	        x = x + it
	    }

	    assert x == 45
	}

	void testRangeStepEach() {
	    x = 0

	    (0..9).step(3) {
	        x = x + it
	    }

	    assert x == 18
	    
	    x = 0

	    (0...10).step(3) {
	        x = x + it
	    }

	    assert x == 18
	}

	void testRangeStepFor() {
	    x = 0

	    for (it in (0..9).step(3)) {
	        x = x + it
	    }

	    assert x == 18
	    
	    x = 0

	    for (it in (0...10).step(3)) {
	        x = x + it
	    }

	    assert x == 18
	}
	
	void testRangeContains() {
	    range = 0..10
	    assert range.contains(0)
	    assert range.contains(10)
	    
	    range = 0...5
	    assert range.contains(0)
	    assert ! range.contains(5)
	}
	
	void testObjectRangeContains() {
	    range = 'a'..'x'
	    assert range.contains('a')
	    assert range.contains('x')
	    assert range.contains('z') == false
	    
	    range = 'b'...'f'
	    assert range.contains('b')
	    assert ! range.contains('g')
	    assert ! range.contains('f')
	    assert ! range.contains('a')
	}
	
	void testRangeToString() {
	    range = 0..10
	    text = range.toString()
	    assert text == "0..10"
	    text = range.inspect()
	    assert text == "0..10"
	    
	    list = [1, 4..10, 9]
	    text = list.toString()
	    assert text == "[1, 4..10, 9]"
	    text = list.inspect()
	    assert text == "[1, 4..10, 9]"
	    
	    range = 0...11
	    text = range.toString()
	    assert text == "0..10"
	    text = range.inspect()
	    assert text == "0..10"
	    
	    list = [1, 4...11, 9]
	    text = list.toString()
	    assert text == "[1, 4..10, 9]"
	    text = list.inspect()
	    assert text == "[1, 4..10, 9]"
	}
	
	void testRangeSize() {
	    range = 1..10
		s = range.size()
	    assert s == 10
	    
	    range = 1...11
		s = range.size()
	    assert s == 10
	}
	
	void testStringRange() {
	    range = 'a'..'d'
	    
	    list = []
	    range.each { list << it }
	    assert list == ['a', 'b', 'c', 'd']
	    
	    s = range.size()
	    assert s == 4
	}
}
